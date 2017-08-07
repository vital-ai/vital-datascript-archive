package commons.scripts

import org.apache.commons.codec.binary.Base64
import org.apache.commons.io.IOUtils

import ai.vital.prime.groovy.VitalPrimeGroovyScript
import ai.vital.prime.groovy.VitalPrimeScriptInterface
import ai.vital.vitalservice.VitalStatus
import ai.vital.vitalservice.query.ResultList
import ai.vital.vitalsigns.VitalSigns
import ai.vital.vitalsigns.model.VITAL_GraphContainerObject
import ai.vital.vitalsigns.model.VitalApp

import com.amazonaws.ClientConfiguration
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.regions.Region
import com.amazonaws.regions.Regions
import com.amazonaws.services.polly.AmazonPollyClient
import com.amazonaws.services.polly.model.OutputFormat
import com.amazonaws.services.polly.model.SynthesizeSpeechRequest
import com.amazonaws.services.polly.model.SynthesizeSpeechResult
import com.amazonaws.services.polly.model.TextType
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.services.s3.model.PutObjectResult

class PollyApiDatascript implements VitalPrimeGroovyScript {

	static AmazonPollyClient pollyClient
	
	static String region
	
	static String pollyAccessKey
	
	static String pollySecretKey
	
	static String publicBucketName
	
	static String privateBucketName
	
	static String s3AccessKey
	
	static String s3SecretKey
	
	static AmazonS3Client s3Client
	
	static void initS3Client(VitalPrimeScriptInterface scriptInterface) {
		
		if(s3Client == null) {
			
			synchronized (PollyApiDatascript.class) {
				
				if(s3Client == null) {
					String orgID = scriptInterface.getOrganization().organizationID.toString()
					String appID = scriptInterface.getApp().appID.toString()
					Map orgMap = VitalSigns.get().getConfig(orgID)
					if(orgMap == null) throw new Exception("No organization config object: ${orgID}")
					
					Map appMap = orgMap.get(appID)
					if(appMap == null) throw new Exception("No app config object: ${appID}")
					
					Map s3api = appMap.get('s3api')
					if(s3api == null) throw new Exception("No s3api config object for org: ${orgID} app: ${appID}")
					
					publicBucketName = s3api.get('publicBucketName')
					if(!publicBucketName) throw new Exception("No s3api.publicBucketName config param")
					privateBucketName = s3api.get('privateBucketName')
					if(!privateBucketName) throw new Exception("No s3api.privateBucketName config param")
					
					s3AccessKey = s3api.get('accessKey')
					if(!s3AccessKey) throw new Exception("No s3api.accessKey config param")
					s3SecretKey = s3api.get('secretKey')
					if(!s3SecretKey) throw new Exception("No s3api.secretKey config param")
					
					ClientConfiguration clientConfiguration = new ClientConfiguration()
					
					//short connection and socket timeouts optimized for aimp processing, original values 50000
					clientConfiguration.setConnectionTimeout(10000)
					clientConfiguration.setSocketTimeout(10000)
					
					BasicAWSCredentials credentials = new BasicAWSCredentials(s3AccessKey, s3SecretKey)
					s3Client = new AmazonS3Client(credentials, clientConfiguration)
					
				}
				
			}
			
		}
	}
	
	static void init(VitalPrimeScriptInterface scriptInterface) {
		
		if(pollyClient == null) {
			
			synchronized (PollyApiDatascript.class) {
				
				if(pollyClient == null) {
					String orgID = scriptInterface.getOrganization().organizationID.toString()
					String appID = scriptInterface.getApp().appID.toString()
					Map orgMap = VitalSigns.get().getConfig(orgID)
					if(orgMap == null) throw new Exception("No organization config object: ${orgID}")
					
					Map appMap = orgMap.get(appID)
					if(appMap == null) throw new Exception("No app config object: ${appID}")
					
					Map pollyApi = appMap.get('pollyApi')
					if(pollyApi == null) throw new Exception("No pollyApi config object for org: ${orgID} app: ${appID}")
					
					pollyAccessKey = pollyApi.get('accessKey')
					if(!pollyAccessKey) throw new Exception("No pollyApi.accessKey config param")
					pollySecretKey = pollyApi.get('secretKey')
					if(!pollySecretKey) throw new Exception("No pollyApi.secretKey config param")
					
					region = pollyApi.get('region')
					if(!region) throw new Exception("No pollyApi.region config param")
					
					
					ClientConfiguration clientConfiguration = new ClientConfiguration()
					
					//short connection and socket timeouts optimized for aimp processing, original values 50000
					clientConfiguration.setConnectionTimeout(10000)
					clientConfiguration.setSocketTimeout(10000)
					
					pollyClient = new AmazonPollyClient(new BasicAWSCredentials(pollyAccessKey, pollySecretKey), new ClientConfiguration());
					pollyClient.setRegion(Region.getRegion(Regions.fromName(region)))
					
				}
				
			}
			
		}
		
	}
	
	@Override
	public ResultList executeScript(VitalPrimeScriptInterface scriptInterface, Map<String, Object> parameters) {

		ResultList rl = new ResultList()
		
		InputStream speechStream = null
		
		try {
			
			init(scriptInterface)
			
			String voiceId = parameters.get('voiceId')
			
			if(!voiceId) {
				throw new Exception("No 'voiceId' parameter")
			}
			
			String text = parameters.get('text')
			if(!text) {
				throw new Exception("No 'text' parameter")	
			}
			
			String textTypeParam = parameters.get('textType')
			TextType textType = TextType.Text
			if(textTypeParam) {
				textType = TextType.fromValue(textTypeParam)
			}
			
			String outputFormatParam = parameters.get('outputFormat')
			
			OutputFormat outputFormat = OutputFormat.Mp3
			if(outputFormatParam) {
				outputFormat = OutputFormat.fromValue(outputFormatParam)
			}
			
			String sampleRate = '22050'
			if(outputFormat == OutputFormat.Pcm) {
				sampleRate = '16000'
			}
			
			String sampleRateParam = parameters.get('sampleRate')
			if(sampleRateParam) {
				sampleRate = sampleRateParam
			}
			
			String output = parameters.get('output')
			if(!output) throw new Exception("No output parameter")
			
			String targetBucket = null
			String outputPath = parameters.get('outputPath')
			String scope = parameters.get('scope')
			
			ObjectMetadata om = null
			
			boolean base64Output = false
			
			if(output == 'results') {
				base64Output = true
			} else if(output == 's3'){
			
				if(!outputPath) {
					throw new Exception("No outputPath param - required for s3 output")
				}
				
				if(!scope) throw new Exception("No scope param - requred for s3 output")
				
				initS3Client(scriptInterface)
				
				if('private'.equalsIgnoreCase(scope)) {
					targetBucket = privateBucketName
				} else if('public'.equalsIgnoreCase(scope)) {
					targetBucket = publicBucketName
				} else throw new Exception("Unknown scope param: " + scope)
			
				
				om = new ObjectMetadata()
				
			} else {
				throw new Exception("Unknown output parameter: ${output} - expected results/s3")
			}
			
			
			SynthesizeSpeechRequest synthReq =
			new SynthesizeSpeechRequest().withText(text).withVoiceId(voiceId).withSampleRate(sampleRate)
			.withTextType(textType)
					.withOutputFormat(outputFormatParam);
					
			SynthesizeSpeechResult synthRes = pollyClient.synthesizeSpeech(synthReq);
			
			if( synthRes.getSdkHttpMetadata().getHttpStatusCode() != 200 ) {
				throw new Exception("Polly service HTTP error: " + synthRes.getSdkHttpMetadata().getHttpStatusCode())
			}
			
			if(om != null) {
				om.setContentType(synthRes.getContentType())
			}
			
			speechStream = synthRes.getAudioStream();
			
			
			VITAL_GraphContainerObject res = new VITAL_GraphContainerObject()
			if(scriptInterface != null) {
				res.generateURI(scriptInterface.getApp())
			} else {
				res.generateURI((VitalApp) null)
			}
			
			if(base64Output) {
			
				ByteArrayOutputStream bos = new ByteArrayOutputStream()
				IOUtils.copy(speechStream, bos);
				speechStream.close()
				speechStream = null
				
				res."base64Data" = Base64.encodeBase64String(bos.toByteArray())
					
			} else {
			
				PutObjectResult putObjectResult = s3Client.putObject(targetBucket, outputPath, speechStream, om)

				res."bucket" = targetBucket
				res."outputPath" = outputPath 

			}
			
			rl.addResult(res)						 
			
		} catch(Exception e) {
			rl.status = VitalStatus.withError(e.localizedMessage)
		} finally {
			IOUtils.closeQuietly(speechStream)
		}
		
		return rl

	}

}
