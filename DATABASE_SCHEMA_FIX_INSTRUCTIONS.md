# Database Schema Fix Instructions

## Problem
The application is failing to start due to PostgreSQL connection issues during Hibernate schema migration. The enhanced payment system models are trying to add many new columns, but the database connections are timing out.

## Solutions (Choose One)

### Solution 1: Use PostgreSQL Admin Tool (Recommended)
1. Open **pgAdmin** or any PostgreSQL client
2. Connect to your `chat_memory_greensuite` database
3. Execute the SQL script: `src/main/resources/sql/fix_payment_schema_migration.sql`
4. Restart the application

### Solution 2: Use Command Line (if PostgreSQL is in PATH)
```bash
# If you have PostgreSQL in your system PATH
psql -h localhost -U postgres -d chat_memory_greensuite -f src/main/resources/sql/fix_payment_schema_migration.sql
```

### Solution 3: Temporary Schema Recreation
1. **BACKUP your data first!**
2. Start the application with the schema-fix profile:
   ```bash
   mvn spring-boot:run -Dspring.profiles.active=schema-fix
   ```
3. This will recreate the tables with the new schema
4. Stop the application and restart normally

### Solution 4: Manual Column Addition (Safest)
Execute these SQL commands in your PostgreSQL client:

```sql
-- Add missing columns to payment_transactions
ALTER TABLE payment_transactions ADD COLUMN IF NOT EXISTS risk_score INTEGER DEFAULT 0;
ALTER TABLE payment_transactions ADD COLUMN IF NOT EXISTS user_agent VARCHAR(255);
ALTER TABLE payment_transactions ADD COLUMN IF NOT EXISTS webhook_received BOOLEAN DEFAULT FALSE;
ALTER TABLE payment_transactions ADD COLUMN IF NOT EXISTS geolocation VARCHAR(255);
ALTER TABLE payment_transactions ADD COLUMN IF NOT EXISTS device_fingerprint VARCHAR(255);
ALTER TABLE payment_transactions ADD COLUMN IF NOT EXISTS risk_level VARCHAR(255) DEFAULT 'LOW';
ALTER TABLE payment_transactions ADD COLUMN IF NOT EXISTS fraud_check_passed BOOLEAN DEFAULT TRUE;
ALTER TABLE payment_transactions ADD COLUMN IF NOT EXISTS fraud_reason VARCHAR(500);
ALTER TABLE payment_transactions ADD COLUMN IF NOT EXISTS processing_fee DECIMAL(15,2) DEFAULT 0.00;
ALTER TABLE payment_transactions ADD COLUMN IF NOT EXISTS metadata JSONB;
ALTER TABLE payment_transactions ADD COLUMN IF NOT EXISTS fraud_analysis JSONB;

-- Add missing columns to payment_accounts
ALTER TABLE payment_accounts ADD COLUMN IF NOT EXISTS verification_level VARCHAR(255) DEFAULT 'BASIC';
ALTER TABLE payment_accounts ADD COLUMN IF NOT EXISTS daily_limit DECIMAL(15,2) DEFAULT 1000.00;
ALTER TABLE payment_accounts ADD COLUMN IF NOT EXISTS monthly_limit DECIMAL(15,2) DEFAULT 10000.00;
ALTER TABLE payment_accounts ADD COLUMN IF NOT EXISTS daily_spent DECIMAL(15,2) DEFAULT 0.00;
ALTER TABLE payment_accounts ADD COLUMN IF NOT EXISTS monthly_spent DECIMAL(15,2) DEFAULT 0.00;
ALTER TABLE payment_accounts ADD COLUMN IF NOT EXISTS is_frozen BOOLEAN DEFAULT FALSE;
ALTER TABLE payment_accounts ADD COLUMN IF NOT EXISTS failed_transaction_count INTEGER DEFAULT 0;
ALTER TABLE payment_accounts ADD COLUMN IF NOT EXISTS successful_transaction_count INTEGER DEFAULT 0;
```

## Configuration Changes Made

### Fixed Issues:
1. **Database URL**: Removed trailing semicolon that was causing connection issues
2. **Connection Pool**: Enhanced HikariCP settings for better stability
3. **Hibernate DDL**: Changed from `update` to `validate` to prevent automatic schema changes
4. **Connection Timeouts**: Increased timeouts to handle larger schema operations

### After Schema Fix:
1. The application should start successfully
2. All payment system features will be fully functional
3. Fraud detection, analytics, and monitoring will work correctly

## Verification
After applying the fix, verify the application starts by checking:
1. No database connection errors in logs
2. Payment endpoints are accessible: `GET /api/payment/credits/packages`
3. System health endpoint works: `GET /api/system/status`

## Rollback (If Needed)
If anything goes wrong:
1. Restore your database backup
2. Revert the application.properties changes
3. Start with the original configuration

---

**Status**: Ready to fix database schema issues ✅ 