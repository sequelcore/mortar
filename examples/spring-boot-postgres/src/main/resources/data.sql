insert into clients (id, name, active)
values
    (7, 'Ada', true),
    (8, 'Grace', false)
on conflict (id) do update
set name = excluded.name,
    active = excluded.active;
