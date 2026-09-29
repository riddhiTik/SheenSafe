# Account Deletion

Google Play requires apps that support account creation to offer an in-app way
to delete the account and its data, not just sign out. Settings → **Delete
Account** implements this.

## What tapping "Delete Account" does

1. Deletes every row keyed by the device's `device_id` from `sensor_samples`,
   `risk_decisions`, `risk_profiles`, and `users` (`SupabaseRepository.deleteAllUserData`).
2. Deletes the underlying Supabase Auth account via the `delete_own_account`
   RPC (`SupabaseRepository.deleteAuthAccount`) — see setup below.
3. Signs out of the (now-deleted) session.
4. Wipes all local `SharedPreferences` (`AppRepository.deleteAllData`).
5. Returns the user to onboarding.

Steps run best-effort in that order so a failure partway through (e.g. no
network) still leaves the device in a clean, logged-out state with local data
gone.

## One-time Supabase setup (required)

The app only ever holds the Supabase **anon key**, so it cannot delete an
`auth.users` row directly — that requires the service-role key, which must
never ship inside the app. Instead, run this once in the Supabase project's
SQL editor to create a `SECURITY DEFINER` RPC that lets a user delete only
their *own* account:

```sql
create or replace function public.delete_own_account()
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  delete from auth.users where id = auth.uid();
end;
$$;

revoke all on function public.delete_own_account() from public;
grant execute on function public.delete_own_account() to authenticated;
```

`auth.uid()` resolves from the caller's JWT, so this can only ever delete the
calling user's own account — it cannot be used to delete anyone else's.

Until this RPC exists, `deleteAuthAccount()` fails silently (it's wrapped in
`runCatching`) and the flow still deletes all data rows and signs the device
out — but the `auth.users` row itself will remain until the RPC is deployed.
Deploy it before submitting to Google Play.
