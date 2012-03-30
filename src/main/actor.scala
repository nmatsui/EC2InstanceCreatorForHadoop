package tc.aws {
  package actor {
    import scala.actors.{Actor,TIMEOUT}
    class EC2Actor extends Actor {
      import tc.aws.ec2.EC2ClientWrapper
      import tc.aws.message._
      private val OWNER = "000000000000"
      private val ACCESS_KEY = "XXXXXXXXXXXXXXXXXXXX"
      private val SECRET_KEY = "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
      private val END_POINT = "ec2.ap-southeast-1.amazonaws.com"

      def act() {
        react {
          case AmiList() => {
            println("ami list")
            connect {ec2 => reply(ec2.getImageIdList(OWNER))}
          }
          case RunInstance(ami, instanceType, group, pem, num) => {
            println("run instance")
            connect {ec2 =>
              val instanceIdList = ec2.runInstances(ami, pem, instanceType, group, num)
              val instanceInfoList = (new EC2Actor).start !? InstanceInfo(instanceIdList)
              Wait.sleep(60){reply(instanceInfoList)}
            }              
          }
          case InstanceInfo(instanceIdList) => {
            println("get instance info for %s".format(instanceIdList))
            connect {ec2 =>
              val instanceInfoList = ec2.getInstanceInfoList(instanceIdList)
              instanceInfoList.map(_.status) match {
                case statusList if statusList.forall(_=="running") => reply(instanceInfoList)
                case _ => Wait.sleep(30){reply((new EC2Actor).start !? InstanceInfo(instanceIdList))}
              }
            }
          }
          case AllInstanceInfo() => {
            println("get all instance info")
            connect {ec2 =>
             reply(ec2.getInstanceInfoList)
            }
          } 
          case _ => throw new Exception
        }
      }
      private def connect(f: EC2ClientWrapper=>Unit):Unit = {
        val ec2 = new EC2ClientWrapper(ACCESS_KEY, SECRET_KEY, END_POINT)
        f(ec2)
      }
    }
    class SSHActor(node:String, pemFile:String, user:String) extends Actor {
      import tc.aws.ssh.SSHClientWrapper
      import tc.aws.message._
      val ssh = new SSHClientWrapper(node, new java.io.File(pemFile), user)
      def act() {
        react {
          case CopyToRemote(src, remoteDir, remoteFileName) => {
            println("scp to remote(%s@%s:%s/%s)".format(user, node, remoteDir, remoteFileName))
            ssh.copyToRemote(src, remoteDir, remoteFileName)
            reply(true)
            act
          }
          case CopyFromRemote(remoteDir, remoteFileName) => {
            println("scp from remote(%s@%s:%s/%s)".format(user, node, remoteDir, remoteFileName))
            reply(ssh.copyFromRemote(remoteDir, remoteFileName))
            act
          }
          case ExecCmd(cmd) => {
            println("exec remote(%s@%s '%s')".format(user, node, cmd))
            reply(ssh.execCmd(cmd))
            act
          }
          case BatchExecCmd(cmdList) => {
            val result = new scala.collection.mutable.ListBuffer[String]
            cmdList.map(cmd => {
              println("exec remote(%s@%s '%s')".format(user, node, cmd))
              result append ssh.execCmd(cmd)
              result append "-----\n"
            })
            reply(result.mkString)
            act
          }
          case Exit() => exit
          case _ => throw new Exception
        }
      }
    }
  }
  package message {
    sealed abstract class Message
    case class AmiList() extends Message
    case class RunInstance(ami:String, instanceType:String, group:String, pem:String, num:Int) extends Message
    case class AllInstanceInfo() extends Message
    case class InstanceInfo(instanceIdList:List[String]) extends Message
    case class CopyToRemote(src:String, remoteDir:String, remoteFileName:String) extends Message
    case class CopyFromRemote(remoteDir:String, remoteFileName:String) extends Message
    case class ExecCmd(cmd:String) extends Message
    case class BatchExecCmd(cmdList:List[String]) extends Message
    case class Exit() extends Message
  }
}
