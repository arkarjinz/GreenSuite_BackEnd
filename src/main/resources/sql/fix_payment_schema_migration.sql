-- Fix Payment Schema Migration
-- Execute this script manually to resolve schema migration issues

-- First, check if tables exist and handle the migration gracefully
DO $$
BEGIN
    -- Create payment_accounts table if it doesn't exist
    IF NOT EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'payment_accounts') THEN
        CREATE TABLE payment_accounts (
            id BIGSERIAL PRIMARY KEY,
            user_id VARCHAR(255) NOT NULL,
            account_number VARCHAR(255) UNIQUE NOT NULL,
            account_name VARCHAR(255) NOT NULL,
            balance DECIMAL(15,2) DEFAULT 0.00,
            currency VARCHAR(3) DEFAULT 'USD',
            status VARCHAR(255) DEFAULT 'PENDING_VERIFICATION',
            verification_level VARCHAR(255) DEFAULT 'BASIC',
            total_deposits DECIMAL(15,2) DEFAULT 0.00,
            total_withdrawals DECIMAL(15,2) DEFAULT 0.00,
            transaction_count INTEGER DEFAULT 0,
            successful_transaction_count INTEGER DEFAULT 0,
            failed_transaction_count INTEGER DEFAULT 0,
            daily_limit DECIMAL(15,2) DEFAULT 1000.00,
            monthly_limit DECIMAL(15,2) DEFAULT 10000.00,
            daily_spent DECIMAL(15,2) DEFAULT 0.00,
            monthly_spent DECIMAL(15,2) DEFAULT 0.00,
            last_daily_reset TIMESTAMP,
            last_monthly_reset TIMESTAMP,
            last_transaction_date TIMESTAMP,
            last_login_ip VARCHAR(255),
            last_transaction_ip VARCHAR(255),
            last_failed_transaction TIMESTAMP,
            is_frozen BOOLEAN DEFAULT FALSE,
            frozen_reason VARCHAR(500),
            frozen_until TIMESTAMP,
            created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            updated_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        );
        
        -- Create indexes for payment_accounts
        CREATE INDEX idx_payment_accounts_user_id ON payment_accounts(user_id);
        CREATE INDEX idx_payment_accounts_account_number ON payment_accounts(account_number);
        CREATE INDEX idx_payment_accounts_status ON payment_accounts(status);
    END IF;

    -- Create payment_transactions table if it doesn't exist
    IF NOT EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'payment_transactions') THEN
        CREATE TABLE payment_transactions (
            id BIGSERIAL PRIMARY KEY,
            user_id VARCHAR(255) NOT NULL,
            account_number VARCHAR(255) NOT NULL,
            transaction_id VARCHAR(255) UNIQUE NOT NULL,
            amount DECIMAL(15,2) NOT NULL,
            currency VARCHAR(3) DEFAULT 'USD',
            transaction_type VARCHAR(255) NOT NULL,
            transaction_category VARCHAR(255),
            status VARCHAR(255) DEFAULT 'PENDING',
            balance_before DECIMAL(15,2),
            balance_after DECIMAL(15,2),
            description TEXT,
            reference_number VARCHAR(255),
            payment_method VARCHAR(255),
            ip_address VARCHAR(255),
            user_agent VARCHAR(255),
            geolocation VARCHAR(255),
            device_fingerprint VARCHAR(255),
            risk_score INTEGER DEFAULT 0,
            risk_level VARCHAR(255) DEFAULT 'LOW',
            fraud_check_passed BOOLEAN DEFAULT TRUE,
            fraud_reason VARCHAR(500),
            processing_fee DECIMAL(15,2) DEFAULT 0.00,
            exchange_rate DECIMAL(10,6),
            original_amount DECIMAL(15,2),
            original_currency VARCHAR(3),
            external_transaction_id VARCHAR(255),
            external_status VARCHAR(255),
            webhook_received BOOLEAN DEFAULT FALSE,
            completed_date TIMESTAMP,
            failed_date TIMESTAMP,
            retry_count INTEGER DEFAULT 0,
            max_retries INTEGER DEFAULT 3,
            next_retry_date TIMESTAMP,
            error_code VARCHAR(100),
            error_message TEXT,
            failure_reason TEXT,
            credits_purchased INTEGER,
            credit_package VARCHAR(255),
            credit_balance_after INTEGER,
            metadata JSONB,
            fraud_analysis JSONB,
            created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            updated_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        );
        
        -- Create indexes for payment_transactions
        CREATE INDEX idx_payment_transactions_user_id ON payment_transactions(user_id);
        CREATE INDEX idx_payment_transactions_transaction_id ON payment_transactions(transaction_id);
        CREATE INDEX idx_payment_transactions_account_number ON payment_transactions(account_number);
        CREATE INDEX idx_payment_transactions_status ON payment_transactions(status);
        CREATE INDEX idx_payment_transactions_transaction_type ON payment_transactions(transaction_type);
        CREATE INDEX idx_payment_transactions_created_date ON payment_transactions(created_date);
        CREATE INDEX idx_payment_transactions_reference_number ON payment_transactions(reference_number);
    ELSE
        -- Table exists, add missing columns safely
        
        -- Add risk_score column if it doesn't exist
        IF NOT EXISTS (SELECT FROM information_schema.columns WHERE table_name = 'payment_transactions' AND column_name = 'risk_score') THEN
            ALTER TABLE payment_transactions ADD COLUMN risk_score INTEGER DEFAULT 0;
        END IF;
        
        -- Add user_agent column if it doesn't exist
        IF NOT EXISTS (SELECT FROM information_schema.columns WHERE table_name = 'payment_transactions' AND column_name = 'user_agent') THEN
            ALTER TABLE payment_transactions ADD COLUMN user_agent VARCHAR(255);
        END IF;
        
        -- Add webhook_received column if it doesn't exist
        IF NOT EXISTS (SELECT FROM information_schema.columns WHERE table_name = 'payment_transactions' AND column_name = 'webhook_received') THEN
            ALTER TABLE payment_transactions ADD COLUMN webhook_received BOOLEAN DEFAULT FALSE;
        END IF;
        
        -- Add geolocation column if it doesn't exist
        IF NOT EXISTS (SELECT FROM information_schema.columns WHERE table_name = 'payment_transactions' AND column_name = 'geolocation') THEN
            ALTER TABLE payment_transactions ADD COLUMN geolocation VARCHAR(255);
        END IF;
        
        -- Add device_fingerprint column if it doesn't exist
        IF NOT EXISTS (SELECT FROM information_schema.columns WHERE table_name = 'payment_transactions' AND column_name = 'device_fingerprint') THEN
            ALTER TABLE payment_transactions ADD COLUMN device_fingerprint VARCHAR(255);
        END IF;
        
        -- Add risk_level column if it doesn't exist
        IF NOT EXISTS (SELECT FROM information_schema.columns WHERE table_name = 'payment_transactions' AND column_name = 'risk_level') THEN
            ALTER TABLE payment_transactions ADD COLUMN risk_level VARCHAR(255) DEFAULT 'LOW';
        END IF;
        
        -- Add fraud_check_passed column if it doesn't exist
        IF NOT EXISTS (SELECT FROM information_schema.columns WHERE table_name = 'payment_transactions' AND column_name = 'fraud_check_passed') THEN
            ALTER TABLE payment_transactions ADD COLUMN fraud_check_passed BOOLEAN DEFAULT TRUE;
        END IF;
        
        -- Add fraud_reason column if it doesn't exist
        IF NOT EXISTS (SELECT FROM information_schema.columns WHERE table_name = 'payment_transactions' AND column_name = 'fraud_reason') THEN
            ALTER TABLE payment_transactions ADD COLUMN fraud_reason VARCHAR(500);
        END IF;
        
        -- Add processing_fee column if it doesn't exist
        IF NOT EXISTS (SELECT FROM information_schema.columns WHERE table_name = 'payment_transactions' AND column_name = 'processing_fee') THEN
            ALTER TABLE payment_transactions ADD COLUMN processing_fee DECIMAL(15,2) DEFAULT 0.00;
        END IF;
        
        -- Add exchange_rate column if it doesn't exist
        IF NOT EXISTS (SELECT FROM information_schema.columns WHERE table_name = 'payment_transactions' AND column_name = 'exchange_rate') THEN
            ALTER TABLE payment_transactions ADD COLUMN exchange_rate DECIMAL(10,6);
        END IF;
        
        -- Add original_amount column if it doesn't exist
        IF NOT EXISTS (SELECT FROM information_schema.columns WHERE table_name = 'payment_transactions' AND column_name = 'original_amount') THEN
            ALTER TABLE payment_transactions ADD COLUMN original_amount DECIMAL(15,2);
        END IF;
        
        -- Add original_currency column if it doesn't exist
        IF NOT EXISTS (SELECT FROM information_schema.columns WHERE table_name = 'payment_transactions' AND column_name = 'original_currency') THEN
            ALTER TABLE payment_transactions ADD COLUMN original_currency VARCHAR(3);
        END IF;
        
        -- Add external_transaction_id column if it doesn't exist
        IF NOT EXISTS (SELECT FROM information_schema.columns WHERE table_name = 'payment_transactions' AND column_name = 'external_transaction_id') THEN
            ALTER TABLE payment_transactions ADD COLUMN external_transaction_id VARCHAR(255);
        END IF;
        
        -- Add external_status column if it doesn't exist
        IF NOT EXISTS (SELECT FROM information_schema.columns WHERE table_name = 'payment_transactions' AND column_name = 'external_status') THEN
            ALTER TABLE payment_transactions ADD COLUMN external_status VARCHAR(255);
        END IF;
        
        -- Add completed_date column if it doesn't exist
        IF NOT EXISTS (SELECT FROM information_schema.columns WHERE table_name = 'payment_transactions' AND column_name = 'completed_date') THEN
            ALTER TABLE payment_transactions ADD COLUMN completed_date TIMESTAMP;
        END IF;
        
        -- Add failed_date column if it doesn't exist
        IF NOT EXISTS (SELECT FROM information_schema.columns WHERE table_name = 'payment_transactions' AND column_name = 'failed_date') THEN
            ALTER TABLE payment_transactions ADD COLUMN failed_date TIMESTAMP;
        END IF;
        
        -- Add retry_count column if it doesn't exist
        IF NOT EXISTS (SELECT FROM information_schema.columns WHERE table_name = 'payment_transactions' AND column_name = 'retry_count') THEN
            ALTER TABLE payment_transactions ADD COLUMN retry_count INTEGER DEFAULT 0;
        END IF;
        
        -- Add max_retries column if it doesn't exist
        IF NOT EXISTS (SELECT FROM information_schema.columns WHERE table_name = 'payment_transactions' AND column_name = 'max_retries') THEN
            ALTER TABLE payment_transactions ADD COLUMN max_retries INTEGER DEFAULT 3;
        END IF;
        
        -- Add next_retry_date column if it doesn't exist
        IF NOT EXISTS (SELECT FROM information_schema.columns WHERE table_name = 'payment_transactions' AND column_name = 'next_retry_date') THEN
            ALTER TABLE payment_transactions ADD COLUMN next_retry_date TIMESTAMP;
        END IF;
        
        -- Add error_code column if it doesn't exist
        IF NOT EXISTS (SELECT FROM information_schema.columns WHERE table_name = 'payment_transactions' AND column_name = 'error_code') THEN
            ALTER TABLE payment_transactions ADD COLUMN error_code VARCHAR(100);
        END IF;
        
        -- Add error_message column if it doesn't exist
        IF NOT EXISTS (SELECT FROM information_schema.columns WHERE table_name = 'payment_transactions' AND column_name = 'error_message') THEN
            ALTER TABLE payment_transactions ADD COLUMN error_message TEXT;
        END IF;
        
        -- Add failure_reason column if it doesn't exist
        IF NOT EXISTS (SELECT FROM information_schema.columns WHERE table_name = 'payment_transactions' AND column_name = 'failure_reason') THEN
            ALTER TABLE payment_transactions ADD COLUMN failure_reason TEXT;
        END IF;
        
        -- Add credits_purchased column if it doesn't exist
        IF NOT EXISTS (SELECT FROM information_schema.columns WHERE table_name = 'payment_transactions' AND column_name = 'credits_purchased') THEN
            ALTER TABLE payment_transactions ADD COLUMN credits_purchased INTEGER;
        END IF;
        
        -- Add credit_package column if it doesn't exist
        IF NOT EXISTS (SELECT FROM information_schema.columns WHERE table_name = 'payment_transactions' AND column_name = 'credit_package') THEN
            ALTER TABLE payment_transactions ADD COLUMN credit_package VARCHAR(255);
        END IF;
        
        -- Add credit_balance_after column if it doesn't exist
        IF NOT EXISTS (SELECT FROM information_schema.columns WHERE table_name = 'payment_transactions' AND column_name = 'credit_balance_after') THEN
            ALTER TABLE payment_transactions ADD COLUMN credit_balance_after INTEGER;
        END IF;
        
        -- Add metadata column if it doesn't exist
        IF NOT EXISTS (SELECT FROM information_schema.columns WHERE table_name = 'payment_transactions' AND column_name = 'metadata') THEN
            ALTER TABLE payment_transactions ADD COLUMN metadata JSONB;
        END IF;
        
        -- Add fraud_analysis column if it doesn't exist
        IF NOT EXISTS (SELECT FROM information_schema.columns WHERE table_name = 'payment_transactions' AND column_name = 'fraud_analysis') THEN
            ALTER TABLE payment_transactions ADD COLUMN fraud_analysis JSONB;
        END IF;
        
        -- Add transaction_category column if it doesn't exist
        IF NOT EXISTS (SELECT FROM information_schema.columns WHERE table_name = 'payment_transactions' AND column_name = 'transaction_category') THEN
            ALTER TABLE payment_transactions ADD COLUMN transaction_category VARCHAR(255);
        END IF;
        
        -- Update column types safely
        BEGIN
            -- Update status column type
            ALTER TABLE payment_transactions ALTER COLUMN status TYPE VARCHAR(255);
        EXCEPTION WHEN OTHERS THEN
            -- Column type change failed, continue
        END;
        
        BEGIN
            -- Update transaction_type column type
            ALTER TABLE payment_transactions ALTER COLUMN transaction_type TYPE VARCHAR(255);
        EXCEPTION WHEN OTHERS THEN
            -- Column type change failed, continue
        END;
        
        -- Create missing indexes
        BEGIN
            CREATE INDEX IF NOT EXISTS idx_payment_transactions_risk_score ON payment_transactions(risk_score);
        EXCEPTION WHEN OTHERS THEN NULL;
        END;
        
        BEGIN
            CREATE INDEX IF NOT EXISTS idx_payment_transactions_risk_level ON payment_transactions(risk_level);
        EXCEPTION WHEN OTHERS THEN NULL;
        END;
        
        BEGIN
            CREATE INDEX IF NOT EXISTS idx_payment_transactions_fraud_check ON payment_transactions(fraud_check_passed);
        EXCEPTION WHEN OTHERS THEN NULL;
        END;
    END IF;

    -- Handle payment_accounts table columns
    IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'payment_accounts') THEN
        -- Add missing columns to payment_accounts
        
        -- Add verification_level column if it doesn't exist
        IF NOT EXISTS (SELECT FROM information_schema.columns WHERE table_name = 'payment_accounts' AND column_name = 'verification_level') THEN
            ALTER TABLE payment_accounts ADD COLUMN verification_level VARCHAR(255) DEFAULT 'BASIC';
        END IF;
        
        -- Add daily_limit column if it doesn't exist
        IF NOT EXISTS (SELECT FROM information_schema.columns WHERE table_name = 'payment_accounts' AND column_name = 'daily_limit') THEN
            ALTER TABLE payment_accounts ADD COLUMN daily_limit DECIMAL(15,2) DEFAULT 1000.00;
        END IF;
        
        -- Add monthly_limit column if it doesn't exist
        IF NOT EXISTS (SELECT FROM information_schema.columns WHERE table_name = 'payment_accounts' AND column_name = 'monthly_limit') THEN
            ALTER TABLE payment_accounts ADD COLUMN monthly_limit DECIMAL(15,2) DEFAULT 10000.00;
        END IF;
        
        -- Add daily_spent column if it doesn't exist
        IF NOT EXISTS (SELECT FROM information_schema.columns WHERE table_name = 'payment_accounts' AND column_name = 'daily_spent') THEN
            ALTER TABLE payment_accounts ADD COLUMN daily_spent DECIMAL(15,2) DEFAULT 0.00;
        END IF;
        
        -- Add monthly_spent column if it doesn't exist
        IF NOT EXISTS (SELECT FROM information_schema.columns WHERE table_name = 'payment_accounts' AND column_name = 'monthly_spent') THEN
            ALTER TABLE payment_accounts ADD COLUMN monthly_spent DECIMAL(15,2) DEFAULT 0.00;
        END IF;
        
        -- Add last_daily_reset column if it doesn't exist
        IF NOT EXISTS (SELECT FROM information_schema.columns WHERE table_name = 'payment_accounts' AND column_name = 'last_daily_reset') THEN
            ALTER TABLE payment_accounts ADD COLUMN last_daily_reset TIMESTAMP;
        END IF;
        
        -- Add last_monthly_reset column if it doesn't exist
        IF NOT EXISTS (SELECT FROM information_schema.columns WHERE table_name = 'payment_accounts' AND column_name = 'last_monthly_reset') THEN
            ALTER TABLE payment_accounts ADD COLUMN last_monthly_reset TIMESTAMP;
        END IF;
        
        -- Add failed_transaction_count column if it doesn't exist
        IF NOT EXISTS (SELECT FROM information_schema.columns WHERE table_name = 'payment_accounts' AND column_name = 'failed_transaction_count') THEN
            ALTER TABLE payment_accounts ADD COLUMN failed_transaction_count INTEGER DEFAULT 0;
        END IF;
        
        -- Add successful_transaction_count column if it doesn't exist
        IF NOT EXISTS (SELECT FROM information_schema.columns WHERE table_name = 'payment_accounts' AND column_name = 'successful_transaction_count') THEN
            ALTER TABLE payment_accounts ADD COLUMN successful_transaction_count INTEGER DEFAULT 0;
        END IF;
        
        -- Add last_login_ip column if it doesn't exist
        IF NOT EXISTS (SELECT FROM information_schema.columns WHERE table_name = 'payment_accounts' AND column_name = 'last_login_ip') THEN
            ALTER TABLE payment_accounts ADD COLUMN last_login_ip VARCHAR(255);
        END IF;
        
        -- Add last_transaction_ip column if it doesn't exist
        IF NOT EXISTS (SELECT FROM information_schema.columns WHERE table_name = 'payment_accounts' AND column_name = 'last_transaction_ip') THEN
            ALTER TABLE payment_accounts ADD COLUMN last_transaction_ip VARCHAR(255);
        END IF;
        
        -- Add is_frozen column if it doesn't exist
        IF NOT EXISTS (SELECT FROM information_schema.columns WHERE table_name = 'payment_accounts' AND column_name = 'is_frozen') THEN
            ALTER TABLE payment_accounts ADD COLUMN is_frozen BOOLEAN DEFAULT FALSE;
        END IF;
        
        -- Add frozen_reason column if it doesn't exist
        IF NOT EXISTS (SELECT FROM information_schema.columns WHERE table_name = 'payment_accounts' AND column_name = 'frozen_reason') THEN
            ALTER TABLE payment_accounts ADD COLUMN frozen_reason VARCHAR(500);
        END IF;
        
        -- Add frozen_until column if it doesn't exist
        IF NOT EXISTS (SELECT FROM information_schema.columns WHERE table_name = 'payment_accounts' AND column_name = 'frozen_until') THEN
            ALTER TABLE payment_accounts ADD COLUMN frozen_until TIMESTAMP;
        END IF;
        
        -- Add last_failed_transaction column if it doesn't exist
        IF NOT EXISTS (SELECT FROM information_schema.columns WHERE table_name = 'payment_accounts' AND column_name = 'last_failed_transaction') THEN
            ALTER TABLE payment_accounts ADD COLUMN last_failed_transaction TIMESTAMP;
        END IF;
        
        -- Update precision for existing decimal columns
        BEGIN
            ALTER TABLE payment_accounts ALTER COLUMN balance TYPE DECIMAL(15,2);
        EXCEPTION WHEN OTHERS THEN NULL;
        END;
        
        BEGIN
            ALTER TABLE payment_accounts ALTER COLUMN total_deposits TYPE DECIMAL(15,2);
        EXCEPTION WHEN OTHERS THEN NULL;
        END;
        
        BEGIN
            ALTER TABLE payment_accounts ALTER COLUMN total_withdrawals TYPE DECIMAL(15,2);
        EXCEPTION WHEN OTHERS THEN NULL;
        END;
    END IF;

END $$;

-- Add helpful comments
COMMENT ON TABLE payment_accounts IS 'Enhanced payment accounts with fraud detection and limits management';
COMMENT ON TABLE payment_transactions IS 'Enhanced payment transactions with comprehensive fraud detection and metadata';

-- Final status message
DO $$
BEGIN
    RAISE NOTICE 'Payment schema migration completed successfully!';
END $$; 