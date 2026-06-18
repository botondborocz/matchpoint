package org.ttproject.di

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

val dotenv = dotenv {
    ignoreIfMissing = true
}
val supabase_url = dotenv["SUPABASE_URL"]
val supabase_anon_key = dotenv["SUPABASE_ANON_KEY"]

val supabase = createSupabaseClient(
    supabaseUrl = supabase_url,
    supabaseKey = supabase_anon_key
) {
    install(Auth)
    install(Postgrest)
}