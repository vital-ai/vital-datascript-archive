

import java.io.Serializable;
import java.util.Map

import org.slf4j.Logger
import org.slf4j.LoggerFactory;

import com.vitalai.domain.commerce.Customer
import com.vitalai.domain.commerce.properties.Property_hasCustomerID;

import ai.vital.prime.groovy.TimeUnit;
import ai.vital.prime.groovy.VitalPrimeGroovyJob
import ai.vital.prime.groovy.VitalPrimeGroovyScript;
import ai.vital.prime.groovy.VitalPrimeScriptInterface
import ai.vital.query.querybuilder.VitalBuilder
import ai.vital.vitalservice.VitalStatus;
import ai.vital.vitalservice.query.ResultList
import ai.vital.vitalservice.query.VitalSelectQuery
import ai.vital.vitalservice.query.VitalSortProperty;
import ai.vital.vitalsigns.model.VitalSegment;

class BraintreeDataSynchronizationJob implements VitalPrimeGroovyJob, VitalPrimeGroovyScript {

	//**********************************
	
	final static VitalSegment productsSegment = VitalSegment.withId('products')
	
	final static VitalSegment customersSegment = VitalSegment.withId('customers')
	
	Map<String, Object> braintreeConfig = [
		environment: 'SANDBOX',
		merchantID: '<merchantId>',
		publicKey: '<publicKey>',
		privateKey: '<privateKey>'
	]
	
	//**********************************
	
	
	private final static Logger log = LoggerFactory.getLogger(BraintreeDataSynchronizationJob.class)
	
	@Override
	public boolean startAtPrettyTime() {
		return false;
	}

	@Override
	public int getInterval() {
		return 24;
	}

	@Override
	public TimeUnit getIntervalTimeUnit() {
		return TimeUnit.HOUR;
	}

	@Override
	public void executeJob(VitalPrimeScriptInterface scriptInterface, Map<String, Serializable> jobDataMap) {

		executeScript(scriptInterface, [:]);
				
	}

	@Override
	public ResultList executeScript(VitalPrimeScriptInterface scriptInterface, Map<String, Object> parameters) {

		Class<? extends VitalPrimeGroovyScript> scriptClass = null
		
		
		try {
			
			scriptClass = Class.forName("commons.scripts.BraintreeDataSynchronizationScript");
			
		} catch(Exception e) {
			log.error(e.localizedMessage, e)
			return
		}
		
		//select all customers, process iteratively
		def builder = new VitalBuilder()
		
		int limit = 1000
		int offset = 0
				
		VitalSelectQuery sq = builder.query {
			
			SELECT {
				
				value segments: [customersSegment] 
				
				value offset: offset
				
				value limit: limit
				
				value sortProperties: [VitalSortProperty.get(Property_hasCustomerID.class)]
				
				node_constraint { Customer.class }
				
			} 
			
		}.toQuery()
		
		int c = 0
		
		while(offset >= 0) {
			
			sq.setOffset(offset)
			
			ResultList rl = scriptInterface.query(sq)
			
			if( rl.status.status != VitalStatus.Status.ok ) {
				
				log.error("Error when querying for input customers list: ${rl.status.message}, offset: ${offset}, limit: ${limit}")
				
				return
				
			}
			
			if( rl.results.size() < limit ) {
				
				//last page
				offset = -1
				
			} else {
			
				offset += 1
				
			}
			
			for(Customer customer : rl) {
				
				c++
				
				log.info("Processing customer ${c}")
				
				
				VitalPrimeGroovyScript script = scriptClass.newInstance()
				
				Map params = [
					braintree: braintreeConfig, 
					customer: customer,
					productsSegment: productsSegment.segmentID.toString(),
					customersSegment: customersSegment.segmentID.toString()
				]
				
				ResultList scriptRL = script.executeScript(scriptInterface, params)
				
				if(scriptRL.status.status == VitalStatus.Status.ok) {
				
					log.info("Customer ${customer.customerID} processed OK: ${scriptRL.status.message}")
						
				} else {
				
					log.error("Customer ${customer.customerID} processing ERROR: ${scriptRL.status.message}")
				
				}
				
			}
			
			
		}
		
	}

}
