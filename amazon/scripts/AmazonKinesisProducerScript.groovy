package commons.scripts

import java.nio.ByteBuffer

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import ai.vital.prime.groovy.VitalPrimeGroovyScript
import ai.vital.prime.groovy.VitalPrimeScriptInterface
import ai.vital.vitalservice.VitalStatus
import ai.vital.vitalservice.query.ResultList
import ai.vital.vitalsigns.VitalSigns

import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.kinesis.AmazonKinesisClient
import com.amazonaws.services.kinesis.model.PutRecordsRequest
import com.amazonaws.services.kinesis.model.PutRecordsRequestEntry
import com.amazonaws.services.kinesis.model.PutRecordsResult
import com.amazonaws.services.kinesis.model.PutRecordsResultEntry
import com.amazonaws.regions.Region
import com.amazonaws.regions.Regions;
/**
 * simple wrapper for kinesis producer. This script is meant to be installed per application basis
 * the configuration is read from vitalsigns config -> orgID -> appID section:
 * kinesis {
 *  accessKey: "accessKey"
 * 	secretKey: "secretKey"
 *  streamName: "streamName"  (optional, if not set then must come with params)
    endpoint: "endpointURL" (optional)
    region: "region" (optional)
 * }
 * @author Derek
 *
 */
class AmazonKinesisProducerScript implements VitalPrimeGroovyScript{

	private final static Logger log = LoggerFactory.getLogger(AmazonKinesisProducerScript.class)
	
	static AmazonKinesisClient amazonKinesisClientInstance
	
	static String streamName
	
	static AmazonKinesisClient getAmazonKinesisClient(VitalPrimeScriptInterface scriptInterface) {
		
		if(amazonKinesisClientInstance == null) {
			
			synchronized (AmazonKinesisProducerScript.class) {
				
				if(amazonKinesisClientInstance == null) {
					
					String orgID = scriptInterface.getOrganization().organizationID
					
					Map orgConfig = VitalSigns.get().getConfig(orgID)
					
					if(orgConfig == null) throw new RuntimeException("No vitalsigns config object for organization: ${orgID}")
					
					String appID = scriptInterface.getApp().appID
					
					Map appConfig = orgConfig.get(appID)
					
					if(appConfig == null) throw new RuntimeException("No vitalsigns app config for organization: ${orgID} / app: ${appID}")

					Map kinesisConfig = appConfig.get('kinesis')
					
					if(kinesisConfig == null) throw new RuntimeException("No kinesis config in orgID/appID")					
					
					String accessKey = kinesisConfig.get('accessKey')
					if(!accessKey) throw new Exception("No kinesis->accessKey config param")
					String secretKey = kinesisConfig.get('secretKey')
					if(!secretKey) throw new Exception("No kinesis->secretKey config param")
					
					//optionally override endpoint
					String endpoint = kinesisConfig.get('endpoint')
					
					String region = kinesisConfig.get('region')
					
					streamName = kinesisConfig.get('streamName')
					if(!streamName) {
//						throw new Exception("No kinesis->streamName config param")
						log.warn("No default streamName in kinesis config")
					}
					
					BasicAWSCredentials credentials = new BasicAWSCredentials(accessKey, secretKey)

					amazonKinesisClientInstance = new AmazonKinesisClient(credentials)

					if(endpoint) {
						log.info("Using non-default kinesis endpoint: ${endpoint}")
						amazonKinesisClientInstance.setEndpoint(endpoint)
					}
					
					if(region) {
						log.info("Using non-default region: ${region}")
						amazonKinesisClientInstance.setRegion(Region.getRegion(Regions.fromName(region)))
					}
					
				}
				
			}
			
		}
		
		return amazonKinesisClientInstance
		
	} 
	
	@Override
	public ResultList executeScript(VitalPrimeScriptInterface scriptInterface, Map<String, Object> parameters) {

		ResultList rl = new ResultList()
		
		try {
			
			AmazonKinesisClient amazonKinesisClient = getAmazonKinesisClient(scriptInterface)
			
			List<String[]> records = parameters.get('records')
			
			String effectiveStreamName = parameters.get('streamName')
			
			if(!effectiveStreamName) {
				if(!streamName) throw new Exception("Default stream name not set, param 'streamName' is required")
				effectiveStreamName = streamName
			}
			
			if(records == null) throw new Exception("No 'records' param")
			if(records.size() == 0) throw new Exception("Empty records list")
			
			PutRecordsRequest putRecordsRequest  = new PutRecordsRequest();
			putRecordsRequest.setStreamName(effectiveStreamName);
			List <PutRecordsRequestEntry> putRecordsRequestEntryList  = new ArrayList<PutRecordsRequestEntry>();
			
			for (int i = 0; i < records.size(); i++) {
				
				List<String> record = records.get(i)
				if(record.size() != 2) throw new Exception("Record ${i} is not a two element string list")
				PutRecordsRequestEntry putRecordsRequestEntry  = new PutRecordsRequestEntry();
				String data = record.get(1)
				if(data == null || data.length() == 0) throw new Exception("Record #${i}: null or empty data")
				putRecordsRequestEntry.setData(ByteBuffer.wrap(data.getBytes()));
				String partitionKey = record.get(0)
				if(partitionKey == null || partitionKey.length() == 0) throw new Exception("Record: #${i}: null or empty partition key")
				putRecordsRequestEntry.setPartitionKey(partitionKey);
				putRecordsRequestEntryList.add(putRecordsRequestEntry);
			}
			
			putRecordsRequest.setRecords(putRecordsRequestEntryList);
			PutRecordsResult putRecordsResult  = amazonKinesisClient.putRecords(putRecordsRequest);

			int successes = 0
			int errors = 0
			for(PutRecordsResultEntry record : putRecordsResult.records) {
				if(record.getErrorCode() != null) {
					errors ++
					rl.status.message = rl.status.message + "\n" + record.getErrorMessage()
				} else {
					successes ++
				}
			}
			rl.status.errors = errors
			rl.status.successes = successes
			
		} catch(Exception e) {
			rl.status = VitalStatus.withError(e.localizedMessage)
		}
		
		return rl;
	}

}
