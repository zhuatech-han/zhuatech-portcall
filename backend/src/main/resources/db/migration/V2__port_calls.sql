-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2

CREATE TABLE agency (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 reference varchar(80) NOT NULL,
 name varchar(120) NOT NULL,
 kind varchar(20) NOT NULL,
 department_id bigint NOT NULL,
 created_by bigint NOT NULL,
 enabled boolean NOT NULL,
 version bigint NOT NULL,
 UNIQUE(reference),
 FOREIGN KEY(department_id) REFERENCES department(id),
 FOREIGN KEY(created_by) REFERENCES account(id),
 CHECK(kind IN ('AGENT','PROVIDER'))
);

CREATE TABLE vessel (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 reference varchar(80) NOT NULL,
 name varchar(120) NOT NULL,
 department_id bigint NOT NULL,
 created_by bigint NOT NULL,
 enabled boolean NOT NULL,
 version bigint NOT NULL,
 UNIQUE(reference),
 FOREIGN KEY(department_id) REFERENCES department(id),
 FOREIGN KEY(created_by) REFERENCES account(id)
);

CREATE TABLE port_call (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 reference varchar(80) NOT NULL,
 vessel_id bigint NOT NULL,
 agent_id bigint NOT NULL,
 department_id bigint NOT NULL,
 location varchar(120) NOT NULL,
 eta timestamp(6) NOT NULL,
 etd timestamp(6) NOT NULL,
 arrived_at timestamp(6),
 departed_at timestamp(6),
 status varchar(30) NOT NULL,
 created_by bigint NOT NULL,
 approved_by bigint,
 closed_by bigint,
 plan_revision bigint NOT NULL,
 version bigint NOT NULL,
 UNIQUE(reference),
 FOREIGN KEY(vessel_id) REFERENCES vessel(id),
 FOREIGN KEY(agent_id) REFERENCES agency(id),
 FOREIGN KEY(department_id) REFERENCES department(id),
 FOREIGN KEY(created_by) REFERENCES account(id),
 FOREIGN KEY(approved_by) REFERENCES account(id),
 FOREIGN KEY(closed_by) REFERENCES account(id),
 CHECK(etd > eta),
 CHECK(departed_at IS NULL OR (arrived_at IS NOT NULL AND departed_at >= arrived_at)),
 CHECK(plan_revision >= 0 AND version >= 1)
);

CREATE TABLE plan_change (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 reference varchar(80) NOT NULL,
 call_id bigint NOT NULL,
 eta timestamp(6) NOT NULL,
 etd timestamp(6) NOT NULL,
 base_revision bigint NOT NULL,
 status varchar(30) NOT NULL,
 created_by bigint NOT NULL,
 reviewed_by bigint,
 version bigint NOT NULL,
 UNIQUE(reference),
 FOREIGN KEY(call_id) REFERENCES port_call(id),
 FOREIGN KEY(created_by) REFERENCES account(id),
 FOREIGN KEY(reviewed_by) REFERENCES account(id),
 CHECK(etd > eta),
 CHECK(base_revision >= 1 AND version >= 1)
);

CREATE TABLE service_order (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 reference varchar(80) NOT NULL,
 call_id bigint NOT NULL,
 provider_id bigint NOT NULL,
 kind varchar(60) NOT NULL,
 critical boolean NOT NULL,
 window_start timestamp(6) NOT NULL,
 window_end timestamp(6) NOT NULL,
 actual_start timestamp(6),
 actual_end timestamp(6),
 status varchar(30) NOT NULL,
 plan_revision bigint NOT NULL,
 created_by bigint NOT NULL,
 responded_by bigint,
 reported_by bigint,
 accepted_by bigint,
 version bigint NOT NULL,
 UNIQUE(reference),
 FOREIGN KEY(call_id) REFERENCES port_call(id),
 FOREIGN KEY(provider_id) REFERENCES agency(id),
 FOREIGN KEY(created_by) REFERENCES account(id),
 FOREIGN KEY(responded_by) REFERENCES account(id),
 FOREIGN KEY(reported_by) REFERENCES account(id),
 FOREIGN KEY(accepted_by) REFERENCES account(id),
 CHECK(window_end > window_start),
 CHECK(actual_end IS NULL OR (actual_start IS NOT NULL AND actual_end >= actual_start)),
 CHECK(version >= 1 AND plan_revision >= 0)
);

ALTER TABLE account ADD COLUMN agency_id bigint;
ALTER TABLE account ADD CONSTRAINT fk_account_agency FOREIGN KEY(agency_id) REFERENCES agency(id);
CREATE TABLE command_record (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 request_key varchar(36) NOT NULL UNIQUE,
 fingerprint varchar(64) NOT NULL,
 result_id bigint NOT NULL
);
CREATE TABLE business_event (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 object_type varchar(30) NOT NULL,
 object_id bigint NOT NULL,
 actor_id bigint NOT NULL,
 action varchar(60) NOT NULL,
 note varchar(1000) NOT NULL,
 snapshot longtext NOT NULL,
 created_at timestamp(6) NOT NULL,
 FOREIGN KEY(actor_id) REFERENCES account(id)
);
CREATE INDEX ix_call_scope ON port_call(department_id,agent_id,status);
CREATE INDEX ix_change_call ON plan_change(call_id,status);
CREATE INDEX ix_service_scope ON service_order(call_id,provider_id,status);
CREATE INDEX ix_events_object ON business_event(object_type,object_id,id);
