-- Ejecuta este SQL una sola vez en Supabase > SQL Editor.
create table if not exists public.ytpro_config (
    id integer primary key check (id = 1),
    config jsonb not null default '{}'::jsonb,
    updated_at timestamptz not null default now()
);

-- El acceso queda reservado al backend mediante la clave service_role.
alter table public.ytpro_config enable row level security;
