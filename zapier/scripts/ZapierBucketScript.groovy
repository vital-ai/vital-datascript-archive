package commons.scripts

import groovy.json.JsonBuilder;
import ai.vital.prime.VitalPrime;
import ai.vital.prime.groovy.VitalPrimeGroovyScript
import ai.vital.prime.groovy.VitalPrimeScriptInterface
import ai.vital.prime.uribucket.UriBucket;
import ai.vital.prime.uribucket.UriBucketComponent;
import ai.vital.prime.uribucket.UriBucketsComponent;
import ai.vital.vitalservice.VitalStatus;
import ai.vital.vitalservice.query.ResultList


class ZapierBucketScript implements VitalPrimeGroovyScript {

	@Override
	public ResultList executeScript(
			VitalPrimeScriptInterface scriptInterface,
			Map<String, Object> parameters) {

		String action = parameters.get("action");
		
		ResultList rl = new ResultList()
		rl.status = VitalStatus.withOK()
		
		try {
			
			if(!action) throw new Exception("No 'action' parameter")
			
			UriBucketsComponent bucketsC = VitalPrime.get().uriBucketsComponent
			
			if(bucketsC == null) throw new Exception("Buckets component disabled")
			UriBucketComponent statsBucket = bucketsC.getStatsBucket();
			if(statsBucket == null) throw new Exception("No stats bucket set")
			
			if(action == 'zapierPing') {
				
				//current and previous
				statsBucket.putUri('zapier', null)
				
				rl.setStatus(VitalStatus.withOKMessage("zapierPing added successfully"));
				
			} else if(action == 'zapierPingHistory'){
			
				List<Map> buckets = []
				
				for(UriBucket b : statsBucket.getLastNBuckets(true, 24)) {
					
					Integer v = b.getHistogram().get('zapier');
					if(v == null) v = 0
					
					
					Map bucket = [
						startDate: b.getBucketStart() != null ? b.getBucketStart().getTime() : null,
						endDate: b.getBucketStop() != null ? b.getBucketStop().getTime() : null,
						value: v
						
					]
					
					buckets.add(bucket)
					//serialize it as a json map ?
				}

								
				JsonBuilder builder = new JsonBuilder(buckets)
				rl.setStatus(VitalStatus.withOKMessage(builder.toPrettyString()))
				
			}
			
		} catch(Exception e) {
			rl.status = VitalStatus.withError(e.localizedMessage);
		}
			
		return rl;
	}
	
}