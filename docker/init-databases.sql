-- Create databases for each service
CREATE DATABASE topdim_coupon;
CREATE DATABASE topdim_order;
CREATE DATABASE topdim_bazaar;
CREATE DATABASE topdim_user;
CREATE DATABASE topdim_payment;
CREATE DATABASE topdim_notification;

-- Grant privileges
GRANT ALL PRIVILEGES ON DATABASE topdim_auth TO topdim;
GRANT ALL PRIVILEGES ON DATABASE topdim_coupon TO topdim;
GRANT ALL PRIVILEGES ON DATABASE topdim_order TO topdim;
GRANT ALL PRIVILEGES ON DATABASE topdim_bazaar TO topdim;
GRANT ALL PRIVILEGES ON DATABASE topdim_user TO topdim;
GRANT ALL PRIVILEGES ON DATABASE topdim_payment TO topdim;
GRANT ALL PRIVILEGES ON DATABASE topdim_notification TO topdim;
