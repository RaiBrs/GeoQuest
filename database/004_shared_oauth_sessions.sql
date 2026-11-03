-- Temporary OAuth login session; it does not replace the seven-day player session.
create table if not exists geoquest_http_session (
  primary_id char(36) not null primary key,
  session_id char(36) not null,
  creation_time bigint not null,
  last_access_time bigint not null,
  max_inactive_interval integer not null,
  expiry_time bigint not null,
  principal_name varchar(100)
);
create unique index if not exists geoquest_http_session_ix1 on geoquest_http_session(session_id);
create index if not exists geoquest_http_session_ix2 on geoquest_http_session(expiry_time);
create index if not exists geoquest_http_session_ix3 on geoquest_http_session(principal_name);
create table if not exists geoquest_http_session_attributes (
  session_primary_id char(36) not null references geoquest_http_session(primary_id) on delete cascade,
  attribute_name varchar(200) not null,
  attribute_bytes bytea not null,
  primary key(session_primary_id,attribute_name)
);
