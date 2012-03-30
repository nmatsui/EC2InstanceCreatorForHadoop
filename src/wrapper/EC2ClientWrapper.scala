package tc.aws {
  import java.util.Date
  import scala.collection.JavaConversions._
  package ec2 {
    import com.amazonaws._
    import com.amazonaws.auth.{AWSCredentials, BasicAWSCredentials}
    import com.amazonaws.services.ec2._
    import com.amazonaws.services.ec2.model._
    import tc.aws.dto._
     
    class EC2ClientWrapper(accesskey:String, secretkey:String, endPoint:String) {
//      private val configuration = new ClientConfiguration()
//                                      .withProtocol(Protocol.HTTPS)
//                                      .withProxyHost("192.168.1.1")
//                                      .withProxyPort(8080)

//      private val ec2 = new AmazonEC2Client(new BasicAWSCredentials(accesskey, secretkey), configuration)
      private val ec2 = new AmazonEC2Client(new BasicAWSCredentials(accesskey, secretkey))
      ec2.setEndpoint(endPoint)
      
      def getImageIdList(owner:String):List[AMI] = {
        val result = ec2.describeImages((new DescribeImagesRequest).withOwners(owner))
        result.getImages.toList.filter(_.getState == "available").map(ami => {
          val tags = ami.getTags.toList.map(t=>(t.getKey, t.getValue))
          AMI(ami.getImageId, ami.getArchitecture, ami.getImageLocation, tags)
        })
      }
  
      def runInstances(imageId:String, keyName:String, instanceType:String, securityGroup:String, number:Int):List[String] = {
        val result = ec2.runInstances((new RunInstancesRequest)
                                        .withImageId(imageId)
                                        .withKeyName(keyName)
                                        .withInstanceType(instanceType)
                                        .withSecurityGroups(securityGroup)
                                        .withMinCount(number)
                                        .withMaxCount(number))
        result.getReservation.getInstances.toList.map(_.getInstanceId)
      }

      def getInstanceInfoList:List[VirtualMachine] = {
        getInstanceInfoList(new DescribeInstancesRequest)
      }
      def getInstanceInfoList(instanceIds:List[String]):List[VirtualMachine] = {
        getInstanceInfoList((new DescribeInstancesRequest).withInstanceIds(instanceIds))
      }
      private def getInstanceInfoList(request:DescribeInstancesRequest):List[VirtualMachine] = {
        val result = ec2.describeInstances(request)
        result.getReservations.toList.flatMap(r => {
          val groupName = r.getGroupNames
          r.getInstances.toList.filter(_.getState.getName != "terminated").map(i =>
            VirtualMachine(i.getInstanceId, i.getImageId, 
                           i.getPrivateDnsName, i.getPrivateIpAddress, 
                           i.getPublicDnsName, i.getPublicIpAddress,
                           i.getLaunchTime, groupName.toList, i.getState.getName)
          )
        })
      }
    }
  }
  package dto {
    case class AMI(
      val imageId:String,
      val architecture:String,
      val location:String,
      val tags:List[(String, String)]
    )
    case class VirtualMachine(
      val instanceId:String,
      val imageId:String,
      val privateDnsName:String,
      val privateIpAddress:String,
      val publicDnsName:String,
      val publicIpAddress:String,
      val launchTime:Date,
      val groupName:List[String],
      val status:String
    )
  }
}
