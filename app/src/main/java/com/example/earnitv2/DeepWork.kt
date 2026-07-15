package com.example.earnitv2

import android.app.NotificationManager
import android.content.Context
import android.os.SystemClock
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
import kotlinx.coroutines.delay

enum class DeepWorkPhase { Inactive, Preparing, Active, GoalComplete }
data class DeepWorkSession(val phase: DeepWorkPhase = DeepWorkPhase.Inactive, val goalSeconds: Long? = 1800L,
    val startedElapsedRealtime: Long = 0L, val startedWallClock: Long = 0L, val baseElapsedSeconds: Long = 0L,
    val preparationEndsElapsedRealtime: Long = 0L, val linkedRuleId: String? = null, val previousInterruptionFilter: Int? = null) {
    fun elapsedSeconds(nowElapsed: Long, nowWall: Long): Long { if (phase != DeepWorkPhase.Active && phase != DeepWorkPhase.GoalComplete) return baseElapsedSeconds; val mono=if(startedElapsedRealtime>0&&nowElapsed>=startedElapsedRealtime) nowElapsed-startedElapsedRealtime else -1; return baseElapsedSeconds+(if(mono>=0) mono else (nowWall-startedWallClock).coerceAtLeast(0))/1000 }
    fun displayPhase(nowElapsed: Long, nowWall: Long) = if(phase==DeepWorkPhase.Active&&goalSeconds!=null&&elapsedSeconds(nowElapsed,nowWall)>=goalSeconds) DeepWorkPhase.GoalComplete else phase
}
object DeepWorkStore {
    private const val PREFS="deep_work"; private const val LINKED="linked_rule"
    fun load(c:Context):DeepWorkSession { val p=c.getSharedPreferences(PREFS,0); return DeepWorkSession(runCatching{DeepWorkPhase.valueOf(p.getString("phase","Inactive")!!)}.getOrDefault(DeepWorkPhase.Inactive), if(p.getBoolean("has_goal",true))p.getLong("goal",1800)else null,p.getLong("started_elapsed",0),p.getLong("started_wall",0),p.getLong("base_elapsed",0),p.getLong("prepare_end",0),p.getString("session_rule",null),if(p.contains("previous_dnd"))p.getInt("previous_dnd",0)else null) }
    fun save(c:Context,s:DeepWorkSession){ c.getSharedPreferences(PREFS,0).edit().putString("phase",s.phase.name).putBoolean("has_goal",s.goalSeconds!=null).putLong("goal",s.goalSeconds?:0).putLong("started_elapsed",s.startedElapsedRealtime).putLong("started_wall",s.startedWallClock).putLong("base_elapsed",s.baseElapsedSeconds).putLong("prepare_end",s.preparationEndsElapsedRealtime).putString("session_rule",s.linkedRuleId).apply{if(s.previousInterruptionFilter==null)remove("previous_dnd")else putInt("previous_dnd",s.previousInterruptionFilter)}.commit() }
    fun linkedRuleId(c:Context)=c.getSharedPreferences(PREFS,0).getString(LINKED,null)
    @Synchronized fun linkRule(c:Context,id:String?){c.getSharedPreferences(PREFS,0).edit().apply{if(id==null)remove(LINKED)else putString(LINKED,id)}.commit()}
    fun begin(c:Context,goal:Long?)=DeepWorkSession(DeepWorkPhase.Preparing,goal,preparationEndsElapsedRealtime=SystemClock.elapsedRealtime()+3000,linkedRuleId=linkedRuleId(c)).also{save(c,it)}
    fun activate(c:Context,s:DeepWorkSession):DeepWorkSession{val n=c.getSystemService(NotificationManager::class.java);val old=if(n.isNotificationPolicyAccessGranted)n.currentInterruptionFilter else null;if(old!=null)n.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE);return s.copy(phase=DeepWorkPhase.Active,startedElapsedRealtime=SystemClock.elapsedRealtime(),startedWallClock=System.currentTimeMillis(),previousInterruptionFilter=old).also{save(c,it)}}
    fun continueOpenEnded(c:Context,s:DeepWorkSession,e:Long)=s.copy(phase=DeepWorkPhase.Active,goalSeconds=null,baseElapsedSeconds=e,startedElapsedRealtime=SystemClock.elapsedRealtime(),startedWallClock=System.currentTimeMillis()).also{save(c,it)}
    fun finish(c:Context,s:DeepWorkSession,e:Long){s.linkedRuleId?.let{EarnItRuleStore.findRule(c,it)?.let{r->RewardLedger.creditDeepWork(c,r,e)}};val n=c.getSystemService(NotificationManager::class.java);if(n.isNotificationPolicyAccessGranted&&s.previousInterruptionFilter!=null)n.setInterruptionFilter(s.previousInterruptionFilter);save(c,DeepWorkSession())}
}
@Composable fun DeepWorkHomeCard(active:Boolean,onClick:()->Unit){val a=if(active)MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary;Card(modifier=Modifier.fillMaxWidth().clickable(onClick=onClick),shape=RoundedCornerShape(18.dp),colors=CardDefaults.cardColors(a.copy(alpha=.1f)),border=BorderStroke(1.dp,a.copy(alpha=.75f))){Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically){Surface(shape=CircleShape,color=a.copy(alpha=.16f)){Icon(Icons.Rounded.Star,null,Modifier.padding(12.dp),tint=a)};Spacer(Modifier.width(14.dp));Column(Modifier.weight(1f)){Text(if(active)"Deep Work Active" else "Start Deep Work",fontWeight=FontWeight.SemiBold,color=a);Text(if(active)"Return to your focus session." else "Silence distractions and focus.")};Text(">")}}}
@OptIn(ExperimentalMaterial3Api::class) @Composable fun DeepWorkSetupSheet(linkedRule:EarnItRuleStore.Rule?,onDismiss:()->Unit,onStart:(Long?)->Unit){var selected by remember{mutableStateOf<Long?>(1800)};ModalBottomSheet(onDismissRequest=onDismiss){Column(Modifier.fillMaxWidth().padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(10.dp)){Icon(Icons.Rounded.Star,null);Text("Start Deep Work",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.SemiBold);Text("Deep Work turns on Do Not Disturb and blocks distracting apps.",textAlign=TextAlign.Center);Text(linkedRule?.let{"Earning through its linked rule (${it.ratioLabel})"}?:"Standalone session — no Reward Time rule is linked.");listOf(null to "No goal",1800L to "30 minutes",3600L to "1 hour",7200L to "2 hours").forEach{(v,l)->Row(Modifier.fillMaxWidth().clickable{selected=v},verticalAlignment=Alignment.CenterVertically){RadioButton(selected==v,{selected=v});Text(l)}};Button({onStart(selected)},Modifier.fillMaxWidth()){Text("Start")}}}}
@Composable fun DeepWorkScreen(session:DeepWorkSession,onActivated:()->Unit,onContinue:(Long)->Unit,onFinish:(Long)->Unit){var ne by remember{mutableLongStateOf(SystemClock.elapsedRealtime())};var nw by remember{mutableLongStateOf(System.currentTimeMillis())};LaunchedEffect(session){while(true){delay(250);ne=SystemClock.elapsedRealtime();nw=System.currentTimeMillis()}};if(session.phase==DeepWorkPhase.Preparing&&ne>=session.preparationEndsElapsedRealtime)LaunchedEffect(Unit){onActivated()};val e=session.elapsedSeconds(ne,nw);val p=session.displayPhase(ne,nw);val remaining=session.goalSeconds?.let{(it-e).coerceAtLeast(0)};Box(Modifier.fillMaxSize().padding(28.dp),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(22.dp)){Icon(Icons.Rounded.Star,null,Modifier.size(72.dp));when(p){DeepWorkPhase.Preparing->{Text("Starting Deep Work",style=MaterialTheme.typography.headlineMedium);Text((((session.preparationEndsElapsedRealtime-ne)/1000)+1).coerceAtLeast(1).toString(),style=MaterialTheme.typography.displayLarge)};DeepWorkPhase.Active->{Text("Deep Work Active");Text(formatDeepWorkDuration(remaining?:e),style=MaterialTheme.typography.displayLarge);Text(if(remaining==null)"elapsed" else "remaining");OutlinedButton({onFinish(e)}){Text("End Session")}};DeepWorkPhase.GoalComplete->{Text("Goal Complete!",style=MaterialTheme.typography.headlineMedium);Text("Total Deep Work Time\n${formatDeepWorkDuration(e)}",textAlign=TextAlign.Center);Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){Button({onContinue(e)}){Text("Continue")};OutlinedButton({onFinish(e)}){Text("Finish")}}};else->Unit}}}}
fun formatDeepWorkDuration(s0:Long):String{val s=s0.coerceAtLeast(0);val h=s/3600;val m=(s%3600)/60;val r=s%60;return if(h>0)"%d:%02d:%02d".format(h,m,r)else"%02d:%02d".format(m,r)}
