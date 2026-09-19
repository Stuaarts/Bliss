-- This location is enabled only in the local-demo and test profiles.
INSERT INTO bliss_user (id, oidc_subject, display_name) VALUES
('20000000-0000-0000-0000-000000000001', 'demo-provider-unmapped', 'Alex Demo');
INSERT INTO user_role (user_id, role) VALUES
('20000000-0000-0000-0000-000000000001', 'CUSTOMER'),
('20000000-0000-0000-0000-000000000001', 'PROVIDER');
INSERT INTO provider_profile (id, display_name, provider_type, description, city) VALUES
('30000000-0000-0000-0000-000000000001', 'Alex Home Services', 'INDIVIDUAL', 'Demonstration provider for everyday home projects.', 'Toronto');
INSERT INTO provider_member (provider_id, user_id, member_role) VALUES
('30000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001', 'OWNER');
INSERT INTO service_offering (id, provider_id, category_id, title, description, price, currency) VALUES
('40000000-0000-0000-0000-000000000001', '30000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000004', 'Home cleaning', 'A fresh start for your space. Sample two-hour cleaning service.', 80.00, 'CAD'),
('40000000-0000-0000-0000-000000000002', '30000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000002', 'Garden refresh', 'Seasonal garden care and tidy-up. Sample offering.', 65.50, 'CAD');
