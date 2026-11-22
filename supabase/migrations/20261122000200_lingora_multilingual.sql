-- Lingora AI 1.0: language-aware conversations and per-language learner courses
alter table public.conversations
  add column if not exists language_code text not null default 'ja',
  add column if not exists learning_method text not null default 'adaptive';

alter table public.conversations
  drop constraint if exists conversations_language_code_check;
alter table public.conversations
  add constraint conversations_language_code_check
  check (language_code ~ '^[a-z]{2,3}$');

alter table public.conversations
  drop constraint if exists conversations_learning_method_check;
alter table public.conversations
  add constraint conversations_learning_method_check
  check (learning_method in ('speaking','reading','adaptive','both'));

alter table public.learner_data
  add column if not exists courses jsonb not null default '{}'::jsonb;

alter table public.learner_data
  drop constraint if exists learner_data_courses_object_check;
alter table public.learner_data
  add constraint learner_data_courses_object_check
  check (jsonb_typeof(courses) = 'object');

create index if not exists conversations_user_language_updated_idx
  on public.conversations (user_id, language_code, updated_at desc);
