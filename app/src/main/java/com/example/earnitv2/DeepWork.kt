package com.example.earnitv2

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

enum class DeepWorkPhase { Inactive, Preparing, Active, GoalComplete }
data class DeepWorkSession(val phase: DeepWorkPhase = DeepWorkPhase.Inactive, val goalSeconds: Long? = 1800L,
    val startedElapsedRealtime: Long = 0L, val startedWallClock: Long = 0L, val baseElapsedSeconds: Long = 0L,
    val preparationEndsElapsedRealtime: Long = 0L, val linkedRuleId: String? = null, val previousInterruptionFilter: Int? = null,
    val sessionId: String = "", val rewardCreditThroughSeconds: Long = 0L, val completed: Boolean = false) {
    fun elapsedSeconds(nowElapsed: Long, nowWall: Long): Long { if (phase != DeepWorkPhase.Active && phase != DeepWorkPhase.GoalComplete) return baseElapsedSeconds; val mono=if(startedElapsedRealtime>0&&nowElapsed>=startedElapsedRealtime) nowElapsed-startedElapsedRealtime else -1; return baseElapsedSeconds+(if(mono>=0) mono else (nowWall-startedWallClock).coerceAtLeast(0))/1000 }
    fun displayPhase(nowElapsed: Long, nowWall: Long) = if(phase==DeepWorkPhase.Active&&goalSeconds!=null&&elapsedSeconds(nowElapsed,nowWall)>=goalSeconds) DeepWorkPhase.GoalComplete else phase
}
object DeepWorkStore {
    private const val PREFS="deep_work"; private const val LINKED="linked_rule"
    fun load(c:Context):DeepWorkSession { val p=c.getSharedPreferences(PREFS,0);val raw=DeepWorkSession(runCatching{DeepWorkPhase.valueOf(p.getString("phase","Inactive")!!)}.getOrDefault(DeepWorkPhase.Inactive),if(p.getBoolean("has_goal",true))p.getLong("goal",1800)else null,p.getLong("started_elapsed",0),p.getLong("started_wall",0),p.getLong("base_elapsed",0),p.getLong("prepare_end",0),p.getString("session_rule",null),if(p.contains("previous_dnd"))p.getInt("previous_dnd",0)else null,p.getString("session_id","")?:"",p.getLong("credit_cursor",0),p.getBoolean("completed",false));val restored=if(raw.displayPhase(SystemClock.elapsedRealtime(),System.currentTimeMillis())==DeepWorkPhase.GoalComplete)raw.copy(phase=DeepWorkPhase.GoalComplete,completed=true)else raw;if(restored!=raw)save(c,restored);return restored }
    fun save(c:Context,s:DeepWorkSession){ c.getSharedPreferences(PREFS,0).edit().putString("phase",s.phase.name).putBoolean("has_goal",s.goalSeconds!=null).putLong("goal",s.goalSeconds?:0).putLong("started_elapsed",s.startedElapsedRealtime).putLong("started_wall",s.startedWallClock).putLong("base_elapsed",s.baseElapsedSeconds).putLong("prepare_end",s.preparationEndsElapsedRealtime).putString("session_rule",s.linkedRuleId).putString("session_id",s.sessionId).putLong("credit_cursor",s.rewardCreditThroughSeconds).putBoolean("completed",s.completed).apply{if(s.previousInterruptionFilter==null)remove("previous_dnd")else putInt("previous_dnd",s.previousInterruptionFilter)}.commit() }
    fun linkedRuleId(c:Context)=c.getSharedPreferences(PREFS,0).getString(LINKED,null)
    fun standaloneBlockedPackages(c:Context):Set<String> = c.getSharedPreferences(PREFS,0).getStringSet("standalone_blocked", emptySet())?.toSet().orEmpty()
    fun setStandaloneBlockedPackages(c:Context,packages:Set<String>){c.getSharedPreferences(PREFS,0).edit().putStringSet("standalone_blocked",packages).apply()}
    @Synchronized fun linkRule(c:Context,id:String?){c.getSharedPreferences(PREFS,0).edit().apply{if(id==null)remove(LINKED)else putString(LINKED,id)}.commit()}
    fun begin(c:Context,goal:Long?)=DeepWorkSession(DeepWorkPhase.Preparing,goal,preparationEndsElapsedRealtime=SystemClock.elapsedRealtime()+3000,linkedRuleId=linkedRuleId(c),sessionId=java.util.UUID.randomUUID().toString()).also{save(c,it)}
    fun activate(c:Context,s:DeepWorkSession):DeepWorkSession{val n=c.getSystemService(NotificationManager::class.java);val old=if(n.isNotificationPolicyAccessGranted)n.currentInterruptionFilter else null;if(old!=null)n.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE);return s.copy(phase=DeepWorkPhase.Active,startedElapsedRealtime=SystemClock.elapsedRealtime(),startedWallClock=System.currentTimeMillis(),previousInterruptionFilter=old).also{save(c,it)}}
    fun continueOpenEnded(c:Context,s:DeepWorkSession,e:Long)=s.copy(phase=DeepWorkPhase.Active,goalSeconds=null,baseElapsedSeconds=e,startedElapsedRealtime=SystemClock.elapsedRealtime(),startedWallClock=System.currentTimeMillis()).also{save(c,it)}
    @Synchronized fun finish(c:Context,s:DeepWorkSession,e:Long):Long{val eligible=eligibleDeepWorkSeconds(s,e);val credited=s.linkedRuleId?.let{EarnItRuleStore.findRule(c,it)}?.let{r->RewardLedger.creditDeepWork(c,r,s.sessionId,eligible).creditedRewardSeconds}?:0L;val n=c.getSystemService(NotificationManager::class.java);if(n.isNotificationPolicyAccessGranted&&s.previousInterruptionFilter!=null)n.setInterruptionFilter(s.previousInterruptionFilter);c.getSharedPreferences(PREFS,0).edit().putString("last_completed_session_id",s.sessionId).putLong("last_completed_elapsed",eligible).putLong("last_completed_at",System.currentTimeMillis()).apply();save(c,DeepWorkSession());return credited}
}

internal fun supportsDeepWorkEarning(type: EarnItRuleStore.RuleType): Boolean = type == EarnItRuleStore.RuleType.EarnRewardTime
internal fun eligibleDeepWorkSeconds(session: DeepWorkSession, elapsedSeconds: Long): Long =
    session.goalSeconds?.let { elapsedSeconds.coerceIn(0L, it) } ?: elapsedSeconds.coerceAtLeast(0L)

internal fun formatDeepWorkReward(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0L)
    val minutes = safe / 60L
    val remainder = safe % 60L
    return when {
        remainder == 0L -> "$minutes min"
        minutes == 0L -> "$remainder sec"
        else -> "$minutes min $remainder sec"
    }
}

@Composable fun DeepWorkRuleSetting(rule: EarnItRuleStore.Rule, editingProtected: Boolean = false, onProtectedAction: () -> Unit = {}) {
    val context = LocalContext.current
    var linkedId by remember(rule.id) { mutableStateOf(DeepWorkStore.linkedRuleId(context)) }
    var conflictId by remember { mutableStateOf<String?>(null) }
    val enabled = linkedId == rule.id
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Deep Work", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Count Deep Work toward earning")
                if (enabled) Text("Every 10 min of Deep Work earns ${rule.rewardSecondsPerProductiveSecond} min Reward Time.", style = MaterialTheme.typography.bodySmall)
            }
            Switch(enabled, onCheckedChange = { checked ->
                if (editingProtected) onProtectedAction()
                else if (!checked) { DeepWorkStore.linkRule(context, null); linkedId = null }
                else { val current = DeepWorkStore.linkedRuleId(context); if (current == null || current == rule.id) { DeepWorkStore.linkRule(context, rule.id); linkedId = rule.id } else conflictId = current }
            })
        }
    }
    conflictId?.let { oldId ->
        val oldName = EarnItRuleStore.findRule(context, oldId)?.productiveName?.ifBlank { "another Rule" } ?: "another Rule"
        AlertDialog(onDismissRequest = { conflictId = null }, title = { Text("Deep Work is already linked") }, text = { Text("Deep Work currently earns through $oldName. Move it to this Rule?") }, confirmButton = { TextButton({ DeepWorkStore.linkRule(context, rule.id); linkedId = rule.id; conflictId = null }) { Text("Move Deep Work") } }, dismissButton = { TextButton({ conflictId = null }) { Text("Cancel") } })
    }
}
@Composable fun DeepWorkHomeCard(active:Boolean,onClick:()->Unit){val a=if(active)MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary;Card(modifier=Modifier.fillMaxWidth().clickable(onClick=onClick),shape=RoundedCornerShape(18.dp),colors=CardDefaults.cardColors(a.copy(alpha=.1f)),border=BorderStroke(1.dp,a.copy(alpha=.75f))){Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically){Surface(shape=CircleShape,color=a.copy(alpha=.16f)){Icon(Icons.Rounded.Star,null,Modifier.padding(12.dp),tint=a)};Spacer(Modifier.width(14.dp));Column(Modifier.weight(1f)){Text(if(active)"Deep Work Active" else "Start Deep Work",fontWeight=FontWeight.SemiBold,color=a);Text(if(active)"Return to your focus session." else "Silence distractions and focus.")};Text(">")}}}
@OptIn(ExperimentalMaterial3Api::class) @Composable fun DeepWorkSetupSheet(linkedRule:EarnItRuleStore.Rule?,apps:List<EarnItRuleStore.LaunchableApp> = emptyList(),onDismiss:()->Unit,onStart:(Long?)->Unit){
    val context=LocalContext.current;var selected by remember{mutableStateOf<Long?>(1800)};var custom by remember{mutableStateOf("45")};var customSelected by remember{mutableStateOf(false)};var showDndRationale by remember{mutableStateOf(false)};var standaloneBlocked by remember{mutableStateOf(DeepWorkStore.standaloneBlockedPackages(context))}
    ModalBottomSheet(onDismissRequest=onDismiss){Column(Modifier.fillMaxWidth().padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(10.dp)){
        Icon(Icons.Rounded.Star,null);Text("Start Deep Work",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.SemiBold)
        Text("Silences distractions, automatically enables Do Not Disturb when available, and blocks distracting apps.",textAlign=TextAlign.Center)
        Text(linkedRule?.let{"Every 10 min of Deep Work earns ${it.rewardSecondsPerProductiveSecond} min Reward Time."}?:"Standalone session — no Reward Time rule is linked.")
        listOf(null to "No goal",1800L to "30 minutes",3600L to "1 hour",7200L to "2 hours").forEach{(v,l)->Row(Modifier.fillMaxWidth().clickable{selected=v;customSelected=false},verticalAlignment=Alignment.CenterVertically){RadioButton(!customSelected&&selected==v,{selected=v;customSelected=false});Text(l)}}
        Row(Modifier.fillMaxWidth().clickable{customSelected=true},verticalAlignment=Alignment.CenterVertically){RadioButton(customSelected,{customSelected=true});OutlinedTextField(custom,{custom=it.filter(Char::isDigit)},label={Text("Custom minutes")},singleLine=true)}
        if(linkedRule==null&&apps.isNotEmpty()){Text("Block during standalone Deep Work",fontWeight=FontWeight.SemiBold);apps.take(6).forEach{app->Row(Modifier.fillMaxWidth().clickable{standaloneBlocked=if(app.packageName in standaloneBlocked)standaloneBlocked-app.packageName else standaloneBlocked+app.packageName;DeepWorkStore.setStandaloneBlockedPackages(context,standaloneBlocked)},verticalAlignment=Alignment.CenterVertically){Checkbox(app.packageName in standaloneBlocked,null);Text(app.name)}}}
        Button({val manager=context.getSystemService(NotificationManager::class.java);val prefs=context.getSharedPreferences("deep_work",0);if(!manager.isNotificationPolicyAccessGranted&&!prefs.getBoolean("dnd_rationale_seen",false))showDndRationale=true else onStart(if(customSelected)custom.toLongOrNull()?.coerceAtLeast(1)?.times(60) else selected)},Modifier.fillMaxWidth()){Text("Start")}
    }}
    if(showDndRationale)AlertDialog(onDismissRequest={showDndRationale=false},title={Text("Silence interruptions automatically")},text={Text("Allow Do Not Disturb access so Deep Work can silence notifications, then restore your previous setting. Deep Work still works without it.")},confirmButton={TextButton({context.getSharedPreferences("deep_work",0).edit().putBoolean("dnd_rationale_seen",true).apply();showDndRationale=false;context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))}){Text("Open settings")}},dismissButton={TextButton({context.getSharedPreferences("deep_work",0).edit().putBoolean("dnd_rationale_seen",true).apply();showDndRationale=false;onStart(if(customSelected)custom.toLongOrNull()?.coerceAtLeast(1)?.times(60) else selected)}){Text("Not now")}})
}
@Composable fun DeepWorkScreen(session:DeepWorkSession,onActivated:()->Unit,onContinue:(Long)->Unit,onFinish:(Long)->Unit){
    val context=LocalContext.current;var ne by remember{mutableLongStateOf(SystemClock.elapsedRealtime())};var nw by remember{mutableLongStateOf(System.currentTimeMillis())}
    LaunchedEffect(session){while(true){delay(250);ne=SystemClock.elapsedRealtime();nw=System.currentTimeMillis()}}
    if(session.phase==DeepWorkPhase.Preparing&&ne>=session.preparationEndsElapsedRealtime)LaunchedEffect(Unit){onActivated()}
    val elapsed=session.elapsedSeconds(ne,nw);val phase=session.displayPhase(ne,nw);val remaining=session.goalSeconds?.let{(it-elapsed).coerceAtLeast(0)}
    val linkedRule=session.linkedRuleId?.let{EarnItRuleStore.findRule(context,it)};val rewardSeconds=linkedRule?.let{deepWorkRewardSeconds(eligibleDeepWorkSeconds(session,elapsed),it.rewardSecondsPerProductiveSecond)}?:0
    if(phase==DeepWorkPhase.GoalComplete)LaunchedEffect(session.sessionId){onFinish(eligibleDeepWorkSeconds(session,elapsed))}
    Box(Modifier.fillMaxSize().padding(28.dp),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(22.dp)){
        Icon(Icons.Rounded.Star,null,Modifier.size(72.dp));when(phase){
            DeepWorkPhase.Preparing->{Text("Starting Deep Work",style=MaterialTheme.typography.headlineMedium);Text("Put your phone down and focus",textAlign=TextAlign.Center);Text((((session.preparationEndsElapsedRealtime-ne)/1000)+1).coerceAtLeast(1).toString(),style=MaterialTheme.typography.displayLarge);Text(if(context.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted)"Do Not Disturb turning on…" else "Do Not Disturb access not granted")}
            DeepWorkPhase.Active->{Text("Deep Work Active");Text(formatDeepWorkDuration(remaining?:elapsed),style=MaterialTheme.typography.displayLarge);Text(if(remaining==null)"elapsed" else "remaining");if(linkedRule!=null){Text("Earning Reward Time for: ${linkedRule.productiveName}");Text("+${formatDeepWorkReward(rewardSeconds)} Reward Time earned")};OutlinedButton({onFinish(eligibleDeepWorkSeconds(session,elapsed))}){Text("End Session")}}
            DeepWorkPhase.GoalComplete->{Text("Goal Complete!",style=MaterialTheme.typography.headlineMedium);Text("Saving your Reward Time...",textAlign=TextAlign.Center)}
            else->Unit
        }
    }}
}
fun formatDeepWorkDuration(s0:Long):String{val s=s0.coerceAtLeast(0);val h=s/3600;val m=(s%3600)/60;val r=s%60;return if(h>0)"%d:%02d:%02d".format(h,m,r)else"%02d:%02d".format(m,r)}
