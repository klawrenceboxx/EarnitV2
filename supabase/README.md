# EarnIt feedback backend

The Android app remains buildable without these resources. Until configured, the feedback form is available for UI testing and preserves the draft, but submission reports that the development backend is unavailable.

## Set up

1. Create a Supabase project and install the Supabase CLI.
2. From this repository, sign in and link the project:

   ```powershell
   npx supabase login
   npx supabase link --project-ref YOUR_PROJECT_REF
   ```

3. Apply the migration. It creates the private `feedback-screenshots` bucket, a fail-closed feedback table, and a server-only rate-limit function:

   ```powershell
   npx supabase db push
   ```

4. Add a random rate-limit salt and deploy:

   ```powershell
   npx supabase secrets set FEEDBACK_RATE_LIMIT_SALT="GENERATE_A_LONG_RANDOM_VALUE"
   npx supabase functions deploy submit-feedback --no-verify-jwt
   ```

   `SUPABASE_URL` and `SUPABASE_SERVICE_ROLE_KEY` are available to hosted Edge Functions. Never put the service-role key in Android configuration.

5. Add local Android values to the uncommitted root `local.properties`:

   ```properties
   FEEDBACK_SUPABASE_URL=https://YOUR_PROJECT_REF.supabase.co
   FEEDBACK_SUPABASE_ANON_KEY=YOUR_PUBLISHABLE_OR_ANON_KEY
   ```

   CI can inject environment variables with the same names.

6. Build on Windows:

   ```powershell
   .\gradlew.bat :app:assembleDebug
   ```

## Verify

Submit text-only and screenshot reports, then confirm:

- `public.feedback` contains one row and a non-sequential `FB-…` reference.
- `status` is `NEW` and cannot be supplied by the client.
- the screenshot object path is stored, the bucket is private, and anonymous list/download fails;
- anonymous select/update/delete on `public.feedback` fails;
- repeating the request with the same `Idempotency-Key` returns the original reference and does not add a row;
- airplane mode produces a local queued report, and reconnecting lets WorkManager deliver it.

The function never persists a raw IP address. It hashes a server-observed address together with a secret salt and the anonymous installation ID for an hourly abuse-control key.
