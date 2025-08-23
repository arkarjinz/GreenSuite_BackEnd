-- Create custom payment system tables for GreenSuite
-- This replaces the Stripe-based payment system

-- Create payment_accounts table
CREATE TABLE IF NOT EXISTS payment_accounts (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    account_number VARCHAR(255) UNIQUE NOT NULL,
    account_name VARCHAR(255) NOT NULL,
    balance DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    currency VARCHAR(10) NOT NULL DEFAULT 'USD',
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_transaction_date TIMESTAMP,
    total_deposits DECIMAL(10,2) DEFAULT 0.00,
    total_withdrawals DECIMAL(10,2) DEFAULT 0.00,
    transaction_count INTEGER DEFAULT 0
);

-- Create payment_transactions table
CREATE TABLE IF NOT EXISTS payment_transactions (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    account_number VARCHAR(255) NOT NULL,
    transaction_id VARCHAR(255) UNIQUE NOT NULL,
    transaction_type VARCHAR(50) NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'USD',
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    description TEXT,
    reference_number VARCHAR(255),
    payment_method VARCHAR(50),
    balance_before DECIMAL(10,2),
    balance_after DECIMAL(10,2),
    credits_purchased INTEGER,
    credit_balance_before INTEGER,
    credit_balance_after INTEGER,
    credit_package VARCHAR(50),
    created_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_date TIMESTAMP,
    metadata TEXT
);

-- Create indexes for better performance
CREATE INDEX IF NOT EXISTS idx_payment_accounts_user_id ON payment_accounts(user_id);
CREATE INDEX IF NOT EXISTS idx_payment_accounts_account_number ON payment_accounts(account_number);
CREATE INDEX IF NOT EXISTS idx_payment_accounts_status ON payment_accounts(status);
CREATE INDEX IF NOT EXISTS idx_payment_accounts_created_date ON payment_accounts(created_date);

CREATE INDEX IF NOT EXISTS idx_payment_transactions_user_id ON payment_transactions(user_id);
CREATE INDEX IF NOT EXISTS idx_payment_transactions_account_number ON payment_transactions(account_number);
CREATE INDEX IF NOT EXISTS idx_payment_transactions_transaction_id ON payment_transactions(transaction_id);
CREATE INDEX IF NOT EXISTS idx_payment_transactions_transaction_type ON payment_transactions(transaction_type);
CREATE INDEX IF NOT EXISTS idx_payment_transactions_status ON payment_transactions(status);
CREATE INDEX IF NOT EXISTS idx_payment_transactions_created_date ON payment_transactions(created_date);
CREATE INDEX IF NOT EXISTS idx_payment_transactions_reference_number ON payment_transactions(reference_number);

-- Create foreign key constraints
ALTER TABLE payment_transactions 
ADD CONSTRAINT fk_payment_transactions_account 
FOREIGN KEY (account_number) REFERENCES payment_accounts(account_number);

-- Add comments for documentation
COMMENT ON TABLE payment_accounts IS 'Payment accounts for GreenSuite users';
COMMENT ON COLUMN payment_accounts.id IS 'Primary key for payment accounts';
COMMENT ON COLUMN payment_accounts.user_id IS 'User ID from the main user system';
COMMENT ON COLUMN payment_accounts.account_number IS 'Unique account number for payment operations';
COMMENT ON COLUMN payment_accounts.account_name IS 'Name of the payment account';
COMMENT ON COLUMN payment_accounts.balance IS 'Current balance in the payment account';
COMMENT ON COLUMN payment_accounts.currency IS 'Currency code for the account (e.g., USD, EUR)';
COMMENT ON COLUMN payment_accounts.status IS 'Account status: ACTIVE, SUSPENDED, CLOSED, PENDING_VERIFICATION';
COMMENT ON COLUMN payment_accounts.total_deposits IS 'Total amount deposited to the account';
COMMENT ON COLUMN payment_accounts.total_withdrawals IS 'Total amount withdrawn from the account';
COMMENT ON COLUMN payment_accounts.transaction_count IS 'Total number of transactions';

COMMENT ON TABLE payment_transactions IS 'Payment transactions for GreenSuite users';
COMMENT ON COLUMN payment_transactions.id IS 'Primary key for payment transactions';
COMMENT ON COLUMN payment_transactions.user_id IS 'User ID from the main user system';
COMMENT ON COLUMN payment_transactions.account_number IS 'Account number for the transaction';
COMMENT ON COLUMN payment_transactions.transaction_id IS 'Unique transaction ID';
COMMENT ON COLUMN payment_transactions.transaction_type IS 'Type of transaction: DEPOSIT, WITHDRAWAL, CREDIT_PURCHASE, REFUND, TRANSFER';
COMMENT ON COLUMN payment_transactions.amount IS 'Transaction amount';
COMMENT ON COLUMN payment_transactions.currency IS 'Currency code for the transaction';
COMMENT ON COLUMN payment_transactions.status IS 'Transaction status: PENDING, PROCESSING, COMPLETED, FAILED, CANCELLED, REFUNDED';
COMMENT ON COLUMN payment_transactions.description IS 'Human-readable description of the transaction';
COMMENT ON COLUMN payment_transactions.reference_number IS 'External reference number for the transaction';
COMMENT ON COLUMN payment_transactions.payment_method IS 'Payment method used: CARD, BANK_TRANSFER, WALLET, CASH, ACCOUNT_BALANCE';
COMMENT ON COLUMN payment_transactions.balance_before IS 'Account balance before the transaction';
COMMENT ON COLUMN payment_transactions.balance_after IS 'Account balance after the transaction';
COMMENT ON COLUMN payment_transactions.credits_purchased IS 'Number of credits purchased in the transaction';
COMMENT ON COLUMN payment_transactions.credit_balance_before IS 'Credit balance before the transaction';
COMMENT ON COLUMN payment_transactions.credit_balance_after IS 'Credit balance after the transaction';
COMMENT ON COLUMN payment_transactions.credit_package IS 'Credit package type: basic, standard, premium, enterprise';

-- Create views for easier querying
CREATE OR REPLACE VIEW deposit_transactions AS
SELECT 
    id,
    user_id,
    account_number,
    transaction_id,
    amount,
    currency,
    status,
    description,
    reference_number,
    payment_method,
    balance_before,
    balance_after,
    created_date,
    processed_date
FROM payment_transactions 
WHERE transaction_type = 'DEPOSIT';

CREATE OR REPLACE VIEW credit_purchase_transactions AS
SELECT 
    id,
    user_id,
    account_number,
    transaction_id,
    amount,
    currency,
    status,
    description,
    payment_method,
    balance_before,
    balance_after,
    credits_purchased,
    credit_balance_before,
    credit_balance_after,
    credit_package,
    created_date,
    processed_date
FROM payment_transactions 
WHERE transaction_type = 'CREDIT_PURCHASE';

CREATE OR REPLACE VIEW completed_transactions AS
SELECT 
    id,
    user_id,
    account_number,
    transaction_id,
    transaction_type,
    amount,
    currency,
    description,
    payment_method,
    balance_before,
    balance_after,
    credits_purchased,
    credit_package,
    created_date,
    processed_date
FROM payment_transactions 
WHERE status = 'COMPLETED';

-- Grant permissions (adjust as needed for your database setup)
-- GRANT SELECT, INSERT, UPDATE ON payment_accounts TO your_app_user;
-- GRANT SELECT, INSERT, UPDATE ON payment_transactions TO your_app_user;
-- GRANT SELECT ON deposit_transactions TO your_app_user;
-- GRANT SELECT ON credit_purchase_transactions TO your_app_user;
-- GRANT SELECT ON completed_transactions TO your_app_user;

-- Verify the table structure
SELECT 
    table_name, 
    column_name, 
    data_type, 
    is_nullable, 
    column_default
FROM information_schema.columns 
WHERE table_name IN ('payment_accounts', 'payment_transactions') 
ORDER BY table_name, ordinal_position; 