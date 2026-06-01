create table if not exists clients (
    id bigint primary key,
    name text not null,
    active boolean not null default true
);
