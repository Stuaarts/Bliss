-- Synthetic database-only fixtures. These opaque workflow payloads are not
-- executable Flow instances. Never start Bliss against this drill database.
DO $$
DECLARE
    customer uuid := '21000000-0000-0000-0000-000000000001';
    admin_id uuid := '21000000-0000-0000-0000-000000000002';
    provider_user uuid := '20000000-0000-0000-0000-000000000001';
    provider uuid := '30000000-0000-0000-0000-000000000001';
    offering uuid := '40000000-0000-0000-0000-000000000002';
    scheduled uuid := '61000000-0000-0000-0000-000000000001';
    completed uuid := '61000000-0000-0000-0000-000000000002';
    disputed uuid := '61000000-0000-0000-0000-000000000003';
    quoted uuid := '61000000-0000-0000-0000-000000000004';
    request_id uuid := gen_random_uuid();
    quote_id uuid := gen_random_uuid();
    conversation_id uuid := gen_random_uuid();
    dispute_id uuid := gen_random_uuid();
BEGIN
    INSERT INTO bliss_user(id,oidc_subject,display_name) VALUES
        (customer,'recovery-fixture-customer','Recovery customer'),
        (admin_id,'recovery-fixture-admin','Recovery admin');
    INSERT INTO user_role(user_id,role) VALUES (customer,'CUSTOMER'),(admin_id,'ADMIN');
    INSERT INTO service_request(id,customer_id,category_id,title,description,city,budget,currency,status,created_at)
        VALUES(request_id,customer,'10000000-0000-0000-0000-000000000002',
            'Recovery quote request','Synthetic garden scope','Toronto',95.50,'CAD','ACCEPTED',now());
    INSERT INTO quote(id,request_id,provider_id,description,price,currency,status,expires_at,created_at)
        VALUES(quote_id,request_id,provider,'Synthetic quote',85.50,'CAD','ACCEPTED',now()+interval '1 day',now());
    INSERT INTO booking(id,customer_id,provider_id,title,description,city,price,currency,status,
                        workflow_instance_id,created_at,offering_id,offering_version,direct_key,scheduled_at)
        VALUES(scheduled,customer,provider,'Scheduled garden','Synthetic direct scope','Toronto',65.50,'CAD','SCHEDULED',
            'recovery-booking-wait',now(),offering,0,gen_random_uuid(),now()+interval '2 days'),
        (completed,customer,provider,'Completed garden','Synthetic direct scope','Toronto',65.50,'CAD','COMPLETED',
            'recovery-booking-completed',now(),offering,0,gen_random_uuid(),now()),
        (disputed,customer,provider,'Disputed garden','Synthetic direct scope','Toronto',65.50,'CAD','DISPUTED',
            'recovery-booking-disputed',now(),offering,0,gen_random_uuid(),now());
    INSERT INTO booking(id,request_id,quote_id,customer_id,provider_id,title,description,city,price,currency,status,
                        workflow_instance_id,created_at)
        VALUES(quoted,request_id,quote_id,customer,provider,'Quoted garden','Synthetic quote scope',
            'Toronto',85.50,'CAD','WAIT_PROVIDER','recovery-quote-wait',now());
    INSERT INTO booking_status_history(id,booking_id,status,actor_user_id,occurred_at)
        SELECT gen_random_uuid(),id,status,customer,now() FROM booking;
    INSERT INTO booking_workflow_start(booking_id,started) SELECT id,true FROM booking;
    INSERT INTO booking_event(id,booking_id,actor_user_id,event_type,scheduled_at,processed_at)
        VALUES(gen_random_uuid(),scheduled,provider_user,'PROVIDER_ACCEPT',now()+interval '2 days',now());
    INSERT INTO payment(booking_id,amount,currency,status)
        SELECT id,price,currency,CASE WHEN id=completed THEN 'CAPTURED' ELSE 'AUTHORIZED' END FROM booking;
    INSERT INTO payment_transaction(id,booking_id,operation)
        SELECT gen_random_uuid(),id,'AUTHORIZE' FROM booking;
    INSERT INTO payment_transaction(id,booking_id,operation) VALUES(gen_random_uuid(),completed,'CAPTURE');
    INSERT INTO conversation(id,booking_id,last_sequence) VALUES(conversation_id,completed,2);
    INSERT INTO conversation_participant(conversation_id,user_id,participant_role)
        VALUES(conversation_id,customer,'CUSTOMER'),(conversation_id,provider_user,'PROVIDER');
    INSERT INTO conversation_message(id,conversation_id,sender_id,client_key,sequence,body)
        VALUES(gen_random_uuid(),conversation_id,customer,gen_random_uuid(),1,'Synthetic customer message: café 🌿'),
        (gen_random_uuid(),conversation_id,provider_user,gen_random_uuid(),2,'Synthetic provider reply');
    INSERT INTO booking_review(id,booking_id,rating,comment)
        VALUES(gen_random_uuid(),completed,5,'Synthetic recovery review');
    INSERT INTO dispute(id,booking_id,opened_by,reason,status,created_at,evidence_deadline,workflow_instance_id)
        VALUES(dispute_id,disputed,customer,'Synthetic unfinished work','UNDER_REVIEW',now(),
            now()+interval '1 day','recovery-dispute-wait');
    INSERT INTO dispute_evidence(id,dispute_id,actor_user_id,actor_role,client_key,body)
        VALUES(gen_random_uuid(),dispute_id,customer,'CUSTOMER',gen_random_uuid(),'Synthetic customer evidence'),
        (gen_random_uuid(),dispute_id,provider_user,'PROVIDER',gen_random_uuid(),'Synthetic provider evidence');
    INSERT INTO dispute_decision(dispute_id,actor_user_id,client_key,outcome,rationale)
        VALUES(dispute_id,admin_id,gen_random_uuid(),'CUSTOMER_FAVORED','Synthetic queued manual decision');
    INSERT INTO dispute_history(id,dispute_id,status,actor_user_id)
        VALUES(gen_random_uuid(),dispute_id,'UNDER_REVIEW',customer);
    INSERT INTO dispute_workflow_start(dispute_id,started) VALUES(dispute_id,true);
    INSERT INTO audit_event(id,actor_user_id,action,resource_id)
        VALUES(gen_random_uuid(),customer,'RECOVERY_FIXTURE',scheduled);
END $$;

INSERT INTO workflow_instance_entity(application_id,instance_id,workflow_name,workflow_namespace,
                                     workflow_version,started_at,status,input)
    SELECT 'bliss-api',instance_id,'synthetic-recovery','com.bliss','fixture-only',now(),1,
        decode('007f80ff','hex') FROM (VALUES ('recovery-booking-wait'),('recovery-quote-wait'),('recovery-dispute-wait')) w(instance_id);
INSERT INTO task_info_entity(application_id,workflow_instance_id,json_pointer,iteration,task_type,
                             is_end_node,context,model)
    SELECT application_id,instance_id,'/fixture-wait',0,1,false,decode('0001feff','hex'),decode('ff0080','hex')
    FROM workflow_instance_entity;
INSERT INTO cloud_event_entity(id,reg_id,source,type,subject,data_content_type,time,data,extensions,version)
    VALUES('recovery-event','fixture-registration','recovery-fixture','fixture.only',
        'recovery-booking-wait','application/octet-stream',now(),decode('0001feff','hex'),decode('ff0080','hex'),1);
CREATE TABLE recovery_drill_marker(id integer PRIMARY KEY, note text NOT NULL);
INSERT INTO recovery_drill_marker VALUES(1,'inside backup snapshot');
