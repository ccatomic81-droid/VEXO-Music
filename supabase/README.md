# Existing VEXO account backend

The recovered Android project uses the existing dedicated VEXO Supabase project at `https://vjkogwihjvlonnyedflq.supabase.co`. The publishable client identifier is not an administrative credential. Account sessions are encrypted on Android; RLS owns each user's library row.

`functions/firebase-signin` was recovered from the deployed function without changing it. It verifies Firebase Google identities before exchanging them for a normal Supabase user session. Administrative keys remain environment bindings. The function's database attempt-budget RPC and `vexo_libraries` ownership policies were provisioned during the original 0.14/0.15 setup; this recovery does not recreate or replace live database objects. A new backend installation must supply an equivalent owned-library schema, revision guard and service-only attempt-budget RPC before enabling these features. Do not disable RLS or grant clients administrative access to bypass a missing setup.

Run `npm ci --prefix supabase/functions/firebase-signin --ignore-scripts` and the function's Node tests before deployment. Publication of 0.16 changes Android/source/release delivery only; the deployed account function and live schema are retained.
