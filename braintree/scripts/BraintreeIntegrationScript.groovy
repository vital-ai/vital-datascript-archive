package commons.scripts

import java.text.DecimalFormat
import java.util.Map

import com.braintreegateway.BraintreeGateway
import com.braintreegateway.ClientTokenRequest
import com.braintreegateway.CustomerRequest;
import com.braintreegateway.Customer as BTCustomer
import com.braintreegateway.Environment
import com.braintreegateway.Result
import com.braintreegateway.Transaction
import com.braintreegateway.TransactionAddressRequest
import com.braintreegateway.TransactionRequest
import com.vitalai.domain.commerce.Customer
import com.vitalai.domain.commerce.Invoice
import com.vitalai.domain.commerce.PaymentInfo;
import com.vitalai.domain.commerce.ShippingInfo;

import ai.vital.domain.Login;
import ai.vital.prime.groovy.VitalPrimeGroovyScript
import ai.vital.prime.groovy.VitalPrimeScriptInterface
import ai.vital.vitalservice.VitalStatus;
import ai.vital.vitalservice.query.ResultElement
import ai.vital.vitalservice.query.ResultList;;
import ai.vital.vitalsigns.model.VITAL_Node
import ai.vital.vitalsigns.model.VitalApp

class BraintreeIntegrationScript implements VitalPrimeGroovyScript {

	public final static vital_customer_id = "vital_customer_id"
	
	public final static vital_invoice_uri = "vital_invoice_uri"
	
	static DecimalFormat priceFormat = new DecimalFormat("0.00")
	
	@Override
	public ResultList executeScript(VitalPrimeScriptInterface scriptInterface, Map<String, Object> params) {

		ResultList rl = new ResultList()
		
		try {
			
			String action = params.get('action')
			if(!action) throw new Exception("No action param")
			
			Map<String, Object> braintreeCfg = params.get("braintree")
			if(braintreeCfg == null) throw new Exception("No braintree param")
			
			BraintreeGateway gateway = initGateway(braintreeCfg)
			
			
			if(action == 'createCustomer') {
				
				Customer customer = params.get('customer')
				if(customer == null) throw new Exception("No customer param")
				String email = params.get('email')
				
				CustomerRequest btRequest = new CustomerRequest()
				if(customer.businessName) btRequest.company(customer.businessName.toString())
				if(email) btRequest.email(email)
				
				if(customer.name) btRequest.lastName(customer.name.toString())
				
				if(customer.mobilePhone) btRequest.phone(customer.mobilePhone.toString())
				if(customer.businessURL) btRequest.website(customer.businessURL.toString())
				
				btRequest.customField(vital_customer_id, customer.customerID.toString())
				
				Result<BTCustomer> result = gateway.customer().create(btRequest)
				
				if(!result.isSuccess()) {
					
					rl.status = VitalStatus.withError(result.getMessage())
					
				} else {
				
					//on successful account creation
					BTCustomer btCustomer = result.getTarget()
					
					customer.braintreeCustomerID = btCustomer.getId()
					
					rl.status = VitalStatus.withOKMessage("Braintree customer created, ID: ${btCustomer.getId()}")
					rl.results.add(new ResultElement(customer, 1D))
				
				}
				
			} else if(action == 'deleteCustomer') {
			
				String braintreeCustomerID = params.get('braintreeCustomerID')
				
				if(!braintreeCustomerID) throw new Exception("No braintreeCustomerID param")
				
				Result<BTCustomer> result = gateway.customer().delete(braintreeCustomerID);
				
				if( ! result.isSuccess() ) {
					
					rl.status = VitalStatus.withError(result.getMessage())
					
				} else {
				
					rl.status = VitalStatus.withOKMessage("Braintree customer deleted, ID: ${braintreeCustomerID}")	
				
				}
				
			} else if(action == 'saleTransaction') {
			
				Customer customer = params.get('customer')
				if(customer == null) throw new Exception("No customer param")
				
				Invoice invoice = params.get('invoice')
				if(invoice == null) throw new Exception("No invoice param")
			
				ShippingInfo shippingInfo = params.get('shippingInfo')
				if(shippingInfo == null) throw new Exception("No shippingInfo param")
				
				PaymentInfo paymentInfo = params.get('paymentInfo')
				if(paymentInfo == null) throw new Exception("No paymentInfo param")
				
				String payment_method_nonce = params.get('payment_method_nonce')
				if(!payment_method_nonce) throw new Exception("No 'payment_method_nonce' param")
				
				Float grandTotal = invoice.grandTotal.floatValue()
				
				TransactionRequest request = new TransactionRequest()
				.customField(vital_customer_id, customer.customerID.toString())
				.customField(vital_invoice_uri, invoice.URI)
				.amount(new BigDecimal(priceFormat.format(grandTotal)))
				.paymentMethodNonce(payment_method_nonce )
			
				if(customer.braintreeCustomerID) request.customerId(customer.braintreeCustomerID.toString())
				
				String streetAddress = ""
				if(shippingInfo.streetAddress1) {
					streetAddress = shippingInfo.streetAddress1.toString()
				}
				
				if(shippingInfo.streetAddress2) {
					if(streetAddress.length() > 0) streetAddress += " \n"
					streetAddress += shippingInfo.streetAddress2.toString()
				}
				
			
				TransactionAddressRequest address = request.shippingAddress()
				if(streetAddress.length() > 0) {
					address.streetAddress(streetAddress)
				}
				
				if(shippingInfo.zipCode) {
					address.postalCode(shippingInfo.zipCode.toString())
				}
				
				if(shippingInfo.state) {
					address.region(shippingInfo.state.toString())
				}
				
				if(shippingInfo.city) {
					address.locality(shippingInfo.city.toString())
				}
				
				if(shippingInfo.fullName) {
					address.lastName(shippingInfo.fullName.toString())
				}
				
				Result<Transaction> result = gateway.transaction().sale(request);
			
				if(!result.isSuccess())	 {
					
					rl.status = VitalStatus.withError(result.getMessage())
					
 				} else {
				 
				 	Transaction braintreeTransaction = result.getTarget()
				 
 					paymentInfo.braintreeTransactionID = braintreeTransaction.getId()
					paymentInfo.paymentStatus = 'done'
					
					rl.status = VitalStatus.withOKMessage("Transaction complete, ID: ${braintreeTransaction.getId()}")
					
					rl.results.add(new ResultElement(paymentInfo, 1D))
					
 				}
				
			} else if(action == 'generateClientToken') {
			
				Customer customer = params.get('customer')
				if(customer == null) throw new Exception("No customer param")
			
				ClientTokenRequest ctr = new ClientTokenRequest()
				if(customer.braintreeCustomerID) ctr.customerId(customer.braintreeCustomerID.toString())
	
				String token = gateway.clientToken().generate(ctr)
			
				VITAL_Node tokenNode = new VITAL_Node()
				tokenNode.generateURI((VitalApp) null)
				tokenNode.name = token
				rl.results.add(new ResultElement(tokenNode, 1D))
				
				rl.status = VitalStatus.withOKMessage("Token generated")
				
			} else {
			
				throw new RuntimeException("Unknown action: ${action}")
				
			}
			
			
		} catch(Exception e) {
		
			rl.status = VitalStatus.withError(e.localizedMessage)
		
		}
		
		return rl;
	}
	
	
	BraintreeGateway initGateway(Map<String, Object> braintreeCfg) {
		
		String btEnvironment = braintreeCfg.get('environment')
		if(!btEnvironment) throw new RuntimeException("No braintree.environment param")
		
		String btMerchantID = braintreeCfg.get('merchantID')
		if(!btMerchantID) throw new RuntimeException("No braintree.merchantID param")
		
		String btPublicKey = braintreeCfg.get('publicKey')
		if(!btPublicKey) throw new RuntimeException("No braintree.publicKey param")
		
		String btPrivateKey = braintreeCfg.get('privateKey')
		if(!btPrivateKey) throw new RuntimeException("No braintree.privateKey param")
		
		Environment env = null
		if("DEVELOPMENT".equalsIgnoreCase(btEnvironment)) {
			env = Environment.DEVELOPMENT
		} else if("SANDBOX".equalsIgnoreCase(btEnvironment)) {
			env = Environment.SANDBOX
		} else if("PRODUCTION".equalsIgnoreCase(btEnvironment)) {
			env = Environment.PRODUCTION
		} else {
			throw new RuntimeException("Unknown Braintree environment: ${btEnvironment}")
		}
		 
		BraintreeGateway braintreeGateway = new BraintreeGateway(
			env,
			btMerchantID,
			btPublicKey,
			btPrivateKey
		);
	
		return braintreeGateway
		
	}

}
