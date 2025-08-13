# 💳 Custom Payment System

## 🎯 **Overview**

This is a complete custom payment system for GreenSuite that replaces Stripe. Users can create payment accounts, make deposits, and purchase credits using their account balance.

## 🏗️ **Architecture**

### **Core Components:**
- **PaymentAccount** - User payment accounts with balance tracking
- **PaymentTransaction** - All payment transactions (deposits, withdrawals, credit purchases)
- **CustomPaymentService** - Business logic for payment operations
- **CustomPaymentController** - REST API endpoints

### **Database Tables:**
- `payment_accounts` - User payment accounts
- `payment_transactions` - All payment transactions

## 🚀 **Features**

### **Payment Accounts:**
- ✅ **Create account** - Users can create one payment account
- ✅ **Account balance** - Track current balance and transaction history
- ✅ **Account statistics** - Total deposits, withdrawals, transaction count
- ✅ **Multiple currencies** - Support for USD, EUR, etc.

### **Deposits:**
- ✅ **Quick deposits** - Instant deposit processing
- ✅ **Multiple payment methods** - Card, Bank Transfer, Wallet, Cash
- ✅ **Reference numbers** - For bank transfer tracking
- ✅ **Transaction tracking** - Complete audit trail

### **Credit Purchases:**
- ✅ **Credit packages** - Basic, Standard, Premium, Enterprise
- ✅ **Account balance withdrawal** - Purchase credits using account balance
- ✅ **Credit balance tracking** - Before/after credit balances
- ✅ **Package validation** - Ensure sufficient account balance

### **Transaction Management:**
- ✅ **Transaction history** - Complete transaction records
- ✅ **Transaction status** - Pending, Processing, Completed, Failed, etc.
- ✅ **Transaction types** - Deposit, Withdrawal, Credit Purchase, Refund, Transfer
- ✅ **Audit trail** - Balance before/after, timestamps, descriptions

## 📋 **API Endpoints**

### **Payment Accounts:**
```
POST   /api/payment/account/create    - Create payment account
GET    /api/payment/account           - Get user's payment account
GET    /api/payment/account/statistics - Get account statistics
```

### **Deposits:**
```
POST   /api/payment/deposit           - Process deposit to account
```

### **Credit Purchases:**
```
POST   /api/payment/credits/purchase  - Purchase credits with account balance
GET    /api/payment/credits/packages  - Get available credit packages
```

### **Transactions:**
```
GET    /api/payment/transactions      - Get transaction history
GET    /api/payment/transactions/{id} - Get specific transaction
```

## 💰 **Credit Packages**

| Package | Credits | Price | Description |
|---------|---------|-------|-------------|
| Basic | 10 | $4.99 | Basic Package - 10 Credits |
| Standard | 25 | $9.99 | Standard Package - 25 Credits |
| Premium | 50 | $17.99 | Premium Package - 50 Credits |
| Enterprise | 100 | $29.99 | Enterprise Package - 100 Credits |

## 🔧 **Setup Instructions**

### **Step 1: Create Database Tables**
```sql
-- Run the SQL script
\i src/main/resources/sql/create_custom_payment_tables.sql
```

### **Step 2: Verify Tables Created**
```sql
-- Check tables exist
SELECT table_name FROM information_schema.tables 
WHERE table_name IN ('payment_accounts', 'payment_transactions');
```

### **Step 3: Test the System**
```bash
# Start your Spring Boot application
./mvnw spring-boot:run
```

## 📝 **Usage Examples**

### **Create Payment Account:**
```json
POST /api/payment/account/create
{
  "accountName": "My Payment Account",
  "currency": "USD"
}
```

### **Make a Deposit:**
```json
POST /api/payment/deposit
{
  "amount": 50.00,
  "paymentMethod": "CARD",
  "description": "Initial deposit",
  "currency": "USD"
}
```

### **Purchase Credits:**
```json
POST /api/payment/credits/purchase
{
  "creditPackage": "standard",
  "currency": "USD"
}
```

## 🔒 **Security Features**

- ✅ **Authentication required** - All endpoints require user login
- ✅ **User isolation** - Users can only access their own accounts
- ✅ **Transaction validation** - Amount and balance validation
- ✅ **Audit logging** - Complete transaction audit trail
- ✅ **Status tracking** - Transaction status management

## 📊 **Database Views**

The system creates these views for easier querying:

- **`deposit_transactions`** - All deposit transactions
- **`credit_purchase_transactions`** - All credit purchase transactions
- **`completed_transactions`** - All completed transactions

## 🎯 **Benefits Over Stripe**

### **Advantages:**
- ✅ **No external dependencies** - No third-party payment processor
- ✅ **Full control** - Complete control over payment logic
- ✅ **No fees** - No transaction fees from payment processors
- ✅ **Simpler integration** - No webhook complexity
- ✅ **Faster processing** - Instant transaction processing
- ✅ **Custom features** - Can add any custom payment features

### **Use Cases:**
- ✅ **Internal credit system** - Perfect for AI credit management
- ✅ **Demo/testing** - Great for development and testing
- ✅ **Small to medium scale** - Suitable for moderate transaction volumes
- ✅ **Custom business logic** - Can implement any payment rules

## 🚀 **Next Steps**

1. **Run the database script** to create tables
2. **Test the API endpoints** with Postman
3. **Integrate with frontend** - Connect to your Next.js app
4. **Add additional features** - Refunds, transfers, etc.

**Your custom payment system is ready to use!** 🎉 