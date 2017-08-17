package commons.scripts

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import ai.vital.prime.groovy.VitalPrimeGroovyScript
import ai.vital.prime.groovy.VitalPrimeScriptInterface
import ai.vital.vitalservice.VitalStatus
import ai.vital.vitalservice.query.ResultList
import ai.vital.vitalsigns.model.VITAL_GraphContainerObject
import ai.vital.vitalsigns.model.VitalApp

import com.stripe.model.Charge
import com.stripe.net.RequestOptions

//installed per app basis with shared configuration for an app
class StripeApiDatascript implements VitalPrimeGroovyScript {

	private final static Logger log = LoggerFactory.getLogger(StripeApiDatascript.class)

	/*no vitalsigns config yet
	static String stripeApiKey = null
		
	static void init(VitalPrimeScriptInterface scriptInterface) {
		
		if(stripeApiKey == null) {
			
			synchronized(StripeApiDatascript.class) {
				
				if(stripeApiKey == null) {
					
					
					
				}
				
			}
		}
		
	}
	*/
	
	@Override
	public ResultList executeScript(VitalPrimeScriptInterface scriptInterface, Map<String, Object> params) {

		ResultList rl = new ResultList()
		
		try {
			
			String action = params.get('action')
			
			if(!action) throw new Exception("No 'action' param")
			
//			init()
			
			if(action == 'charge') {
				
				String apiKey = params.get('apiKey')
				if(!apiKey) throw new Exception("No apiKey param")
				
				String token = params.get('token')
				if(!token) throw new Exception("No token param")
				
				Number amount = params.get('amount')
				if(amount == null) throw new Exception("No amount param")
				
				String currency = params.get('currency')
				if(!currency) throw new Exception("No currency param")
				
				String description = params.get('description')
				
				Map<String, String> metadata = params.get('metadata')
				
				Map<String, Object> checkoutParams = new HashMap<String, Object>();
				checkoutParams.put("amount", amount.intValue());
				checkoutParams.put("currency", currency);
				checkoutParams.put("description", description);
				if(metadata != null && metadata.size() > 0) {
					checkoutParams.put("metadata", metadata);
				}
				checkoutParams.put("source", token);
				
				
				def options = RequestOptions.builder().setApiKey(apiKey).build()
				
				def charge = Charge.create(checkoutParams, options);
				
				VITAL_GraphContainerObject res = new VITAL_GraphContainerObject().generateURI((VitalApp) null)
				res."id" = charge.getId()
				res."json" = charge.toJson()
	
				rl.addResult(res)
				
			} else {
			
				throw new Exception("Unknown action param: " + action);
				
			}
		} catch(Exception e) {
		
			rl.status = VitalStatus.withError(e.localizedMessage)
			
		}
		
		return rl;
	}

}
