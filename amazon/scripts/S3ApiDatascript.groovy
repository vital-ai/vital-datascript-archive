package commons.scripts

import ai.vital.prime.groovy.VitalPrimeGroovyScript
import ai.vital.prime.groovy.VitalPrimeScriptInterface
import ai.vital.query.querybuilder.VitalBuilder
import ai.vital.vitalservice.VitalStatus;
import ai.vital.vitalservice.query.ResultList
import ai.vital.vitalservice.query.VitalSelectQuery
import ai.vital.vitalsigns.VitalSigns;
import ai.vital.vitalsigns.model.VitalSegment
import ai.vital.vitalsigns.model.property.URIProperty;
import ai.vital.domain.File
import ai.vital.domain.File_PropertiesHelper;

import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.CopyObjectRequest;
import com.amazonaws.services.s3.model.CopyObjectResult;
import com.amazonaws.services.s3.model.ObjectMetadata;
import java.util.regex.Matcher
import java.util.regex.Pattern
import org.apache.commons.codec.binary.Base64

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * A datascript that provides simple API based on files stored in S3
 * @author Derek
 *
 */
class S3ApiDatascript implements VitalPrimeGroovyScript {

	private final static Logger log = LoggerFactory.getLogger(S3ApiDatascript.class)
	
	private final static Pattern s3URLPattern = Pattern.compile('^s3\\:\\/\\/([^\\/]+)\\/(.+)$', Pattern.CASE_INSENSITIVE)
	
	static String publicBucketName
	
	static String privateBucketName
	
	static String accessKey
	
	static String secretKey
	
	static AmazonS3Client s3Client
	
	static VitalSegment filesSegment

	static enum FileScope {
		Public,
		Private
		
		static FileScope fromString(String s) {
			if('public'.equalsIgnoreCase(s)) {
				return Public
			} else if('private'.equalsIgnoreCase(s)) {
				return Private
			} else {
				throw new Exception("Unkown file scope: ${s}")
			}
		}
	}
	
	static class FileURL {
		
		public String getFullURL() {
			return 's3://' + ( bucket ) + '/' + relativePath
		}
		
		String bucket
		
		String relativePath
		
	}
		
	static void init(VitalPrimeScriptInterface scriptInterface) {
		
		if(s3Client == null) {
			
			synchronized (S3ApiDatascript.class) {
				
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
					
					accessKey = s3api.get('accessKey')
					if(!accessKey) throw new Exception("No s3api.accessKey config param")
					secretKey = s3api.get('secretKey')
					if(!secretKey) throw new Exception("No s3api.secretKey config param")
					
					String segmentID = s3api.get('segment')
					if(!segmentID) throw new Exception("No segment config param")
					
					filesSegment = scriptInterface.getSegment(segmentID)
					if(filesSegment == null) throw new Exception("Files segment not found: ${segmentID}")
					
					BasicAWSCredentials credentials = new BasicAWSCredentials(accessKey, secretKey)
					s3Client = new AmazonS3Client(credentials)
					
				}
				
			}
			
		}
		
	}

	@Override
	public ResultList executeScript(VitalPrimeScriptInterface scriptInterface, Map<String, Object> parameters) {

		ResultList rl = new ResultList()
		
		try {
			
			init(scriptInterface)
			
			String action = parameters.get('action')
			
			if(!action) throw new Exception("No action parameter")
			

			String profile = parameters.get('profile')
			if(!profile) throw new Exception("No profile parameter")
			
			String _scope = parameters.get('scope')
			if(!_scope) throw new Exception("No scope parameter")
			FileScope scope = FileScope.fromString(_scope)
			
			
			//desired path
			String path = parameters.get('path')
			if(!path) throw new Exception("No path parameter")
		
			if(path.startsWith("/")) throw new Exception("Path must not start with slash")
			
			//edit should not require paths etc
			if(action == 'edit') {
				
				String fileURI = parameters.get('fileURI')
				if(!fileURI) throw new Exception("No fileURI param")
				
				String name = parameters.get('name')
				if(!name) throw new Exception("No name param!")
				
				String fileType = parameters.get('fileType')
				if(!fileType) throw new Exception("No fileType param")
				
				//select file
				File f = selectFileByURI(scriptInterface, fileURI)
				if(f == null) throw new Exception("File not found: ${fileURI}")
				
				String s3URL = f.fileURL
				
				
				Matcher matcher = s3URLPattern.matcher(s3URL)
				
				if(!matcher.matches()) {
					throw new Exception("Invalid internal resource URL")
				}
				
				String bucket = matcher.group(1)
				String key = matcher.group(2)
				
				//check if owner has changed
				String currentOwner = f.owner
				FileScope currentScope = FileScope.fromString(f.fileScope.toString())
				
				String currentPath = f.fileName
				
//				String _scope = parameters.get('scope')
//				if(!_scope) throw new Exception("No scope parameter")
				
				f.name = name
				f.fileType = fileType
				f.timestamp = System.currentTimeMillis()
				
				//these 3 params determine physcial location
				f.fileScope = scope.name()
				f.fileName = path
				f.owner = profile
				
				boolean moved = false
				
				if(currentOwner != profile || currentScope != scope || currentPath != path) {
					
					//path changes only if scope changes
					FileURL currentURL = fileURL(scriptInterface, currentScope, currentOwner, currentPath)
					FileURL newURL = fileURL(scriptInterface, scope, profile, path)
					
					
					//first move file, then save node
					s3Client.copyObject(currentURL.bucket, currentURL.relativePath, newURL.bucket, newURL.relativePath)
					
					//delete original location
					s3Client.deleteObject(currentURL.bucket, currentURL.relativePath)
					
					f.fileURL = newURL.getFullURL()
					
					moved = true
					
				}
				
				scriptInterface.save(filesSegment, f)
				rl.addResult(f)
				rl.status = VitalStatus.withOKMessage("File updated ${moved ? 'and moved into another location' : ''}");
				return rl
				
				
			}
			
			
			FileURL fileURL = fileURL(scriptInterface, scope, profile, path)
						
			if(action == 'create') {
				
				String base64 = parameters.get('base64')
				byte[] data = parameters.get('data')
				String sourceBucket = parameters.get('sourceBucket')
				String sourceKey = parameters.get('sourceKey')
				Boolean deleteOnSuccess = parameters.get('deleteOnSuccess')
				if(deleteOnSuccess == null) deleteOnSuccess = false
				int i= 0
				if(base64) i++
				if(data) i++
				if(sourceBucket || sourceKey) i++
				if(i == 0) throw new Exception("No base64, data nor (sourceBucket, sourceKey) params") 
				
				if( i > 1) throw new Exception("Too many data sources, expected exactly one of: base64, data nor (sourceBucket, sourceKey) params")
				
				String name = parameters.get('name')
				if(!name) throw new Exception("No name param!")
				
				String fileType = parameters.get('fileType')
				if(!fileType) throw new Exception("No fileType param")
				
				Boolean overwrite = parameters.get('overwrite')
				if(overwrite == null) throw new Exception("No overwrite boolean param")

				if(overwrite) {

//					deleteFile(scriptInterface, fileURL);
									
				} else {
				
					List<File> existing = selectFiles(scriptInterface, fileURL);
				
					if(existing.size() > 0) throw new Exception("File already exists: ${fileURL.relativePath}")
					
				}
				
				if(sourceBucket || sourceKey) {
					
					if(sourceBucket && !sourceKey) throw new Exception("No sourceKey param, required with sourceBucket")
					
					if(!sourceBucket && sourceKey) throw new Exception("No sourceBucket param, required with sourceKey")
					
					//check if exists
					ObjectMetadata om = s3Client.getObjectMetadata(sourceBucket, sourceKey)
					if(om == null) throw new Exception("No object metadata!")
					
				} else if(base64) {
				
					data = Base64.decodeBase64(base64)
				
				} else if(data){
				
				}
				
				

				if(overwrite) {
					
					deleteFile(scriptInterface, fileURL);
					
				}
				
				if(sourceBucket || sourceKey) {
					
					s3Client.copyObject(sourceBucket, sourceKey, fileURL.bucket, fileURL.relativePath)
					
				} else {
				
					ByteArrayInputStream bis = new ByteArrayInputStream(data)
				
					ObjectMetadata om = new ObjectMetadata()
					
					om.setContentLength(data.length)
					
					s3Client.putObject(fileURL.bucket, fileURL.relativePath, bis, om)
				
				}

				//create new node
				File newNode = new File();
				newNode.generateURI(scriptInterface.getApp())
				newNode.name = name
				newNode.fileScope = scope.name()
				newNode.fileURL = fileURL.getFullURL()
				newNode.timestamp = System.currentTimeMillis()
				newNode.fileName = path
				newNode.fileType = fileType
				newNode.owner = profile
				
				scriptInterface.save(filesSegment, newNode)
//				if(saveRL.status.status != VitalStatus.Status.ok) throw new Exception("Error when saving new file node: ${saveRL.status.message}")
				
				rl.addResult(newNode)
				
				if((sourceBucket || sourceKey) && deleteOnSuccess.booleanValue()){
					
					s3Client.deleteObject(sourceBucket, sourceKey)
					
				}
				
			} else if(action == 'delete') {
			
				int del = deleteFile(scriptInterface, fileURL)
				
				rl.status.successes = del
			
				if(del > 0) {
					
					s3Client.deleteObject(fileURL.bucket, fileURL.relativePath)
					
				}
				
			} else if(action == 'get') {
			
				List<File> files = selectFiles(scriptInterface, fileURL)
				
				for(File f : files) {
					rl.addResult(f);
				}		
			
				rl.totalResults = files.size()
			
			} else if(action == 'edit') {
			
				String name = parameters.get('name')
				if(!name) throw new Exception("No name param!")
				
				String fileType = parameters.get('fileType')
				if(!fileType) throw new Exception("No fileType param")
			
				List<File> existing = selectFiles(scriptInterface, fileURL);
			
				if(existing.size() == 0) throw new Exception("File not found: ${fileURL.relativePath}")

				
				//edit the first item
				File firstMatch = existing.get(0)
				
				
				
				
			} else {
				throw new Exception("Unknown action: ${action}")
			}
			
		} catch(Exception e) {
			e.printStackTrace()
			rl.status = VitalStatus.withError(e.localizedMessage)
		}
		
		
		return rl;
	}
	
	
	private int deleteFile(VitalPrimeScriptInterface scriptInterface, FileURL fileURL) throws Exception {
		
		List<File> l = selectFiles(scriptInterface, fileURL);
		
		if(l.size() == 0) return 0
		List<URIProperty> uris = []
		for(File f : l) {
			uris.add(URIProperty.withString(f.URI))
		}
		
		VitalStatus status = scriptInterface.delete(uris)
		if(status.status != VitalStatus.Status.ok) {
			throw new Exception("Error when deleting existing file: ${status.message}")
		}
		
		return l.size()
		
	}
	
	private List<File> selectFiles(VitalPrimeScriptInterface scriptInterface, FileURL fileURL) throws Exception {
		
		VitalSelectQuery sq = new VitalBuilder().query {
			SELECT {
				value segments: [filesSegment]
				value offset: 0
				value limit: 10
				
				node_constraint { File.class }
					
				node_constraint { ((File_PropertiesHelper) File.props()).fileURL.equalTo(fileURL.getFullURL()) }
					
			}
		}.toQuery()
		
		ResultList rl = scriptInterface.query(sq)
		if(rl.status.status != VitalStatus.Status.ok) {
			throw new Exception("Error when selecting existing file: ${rl.status.message}")
		}
		
//		if(rl.results.size() > 1) throw new Exception("More than 1 file with path exists: ${fileURL}") 
		
		List<File> l = []
		
		for(File f : rl.iterator(File.class)) {
			l.add(f)
		}
		
		return l
	}
	
	private File selectFileByURI(VitalPrimeScriptInterface scriptInterface, String fileURI) {
		
		VitalSelectQuery sq = new VitalBuilder().query {
			
			SELECT {
				
				value segments: [filesSegment]
				value offset: 0
				value limit: 10
				
				node_constraint { File.class }
				
				node_constraint { "URI eq ${fileURI}"} 
				
			}
			
		}.toQuery()
		
		ResultList rl = scriptInterface.query(sq)
		if(rl.status.status != VitalStatus.Status.ok) {
			throw new Exception("Error when selecting existing file: ${rl.status.message}")
		}
		
		return (File) rl.first()
		
	}
	
	private FileURL fileURL(VitalPrimeScriptInterface scriptInterface, FileScope scope, String profile, String path) {
		
		String orgID = scriptInterface.getOrganization().organizationID.toString()
		String appID = scriptInterface.getApp().appID.toString()
		
		FileURL url = new FileURL()
		url.bucket = scope == FileScope.Public ? publicBucketName : privateBucketName
		url.relativePath = orgID + '/' + appID + '/' + URLEncoder.encode(profile, 'UTF-8') + '/' + path
		return url
		
		
	}
	
}
