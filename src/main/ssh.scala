package tc.aws {
  package main {
    import tc.aws.actor._
    import tc.aws.message._
    import scala.collection.mutable.ListBuffer
    object SSH {
      def main(args:Array[String]):Unit = {
        val masterDns = args(0)
        val slaveDnsList = Nil
        val pem = args(1)
        val templateDir = args(2)
       
        val executer = new SSHExecuter(masterDns, slaveDnsList, pem, templateDir)
        executer.mkHadoopDir
        executer.setHadoopSSHAuthByPem
        executer.setHadoopSSHAuthByGenKey
        executer.putConfFile
      }
      private class SSHExecuter(masterDns:String, slaveDnsList:List[String], pem:String, templateDir:String) {
        import scala.actors.Actor
        def mkHadoopDir = execute {actorList =>
          val cmdForMkHdpDir = mkList[String] {list=>
            list append "mkdir /mnt/hadoop"
            list append "chown hadoop:hadoop /mnt/hadoop"
          }

          actorList append (new SSHActor(masterDns,pem,"root")).start
          slaveDnsList.map(s=>(new SSHActor(s,pem,"root")).start).foreach(actorList append _)
          
          val mkHdpDirFutureList = actorList.map(actor => {
            actor !! BatchExecCmd(cmdForMkHdpDir)
          })
          mkHdpDirFutureList.foreach(_())
        }
        def setHadoopSSHAuthByPem = execute {actorList =>
          val cmdForSSHByPem = mkList[String] {list=>
            list append "mkdir /home/hadoop/.ssh"
            list append "chown hadoop:hadoop /home/hadoop/.ssh"
            list append "chmod 700 /home/hadoop/.ssh"
            list append "cat ~/.ssh/authorized_keys >> /home/hadoop/.ssh/authorized_keys"
            list append "chown hadoop:hadoop /home/hadoop/.ssh/authorized_keys"
            list append "chmod 600 /home/hadoop/.ssh/authorized_keys"
          }

          actorList append (new SSHActor(masterDns,pem,"root")).start
          slaveDnsList.map(s=>(new SSHActor(s,pem,"root")).start).foreach(actorList append _)
          
          val sshByPemFutureList = actorList.map(actor => {
            actor !! BatchExecCmd(cmdForSSHByPem)
          })
          sshByPemFutureList.foreach(_())
        }
        def setHadoopSSHAuthByGenKey = execute {actorList =>
          val cmdForSSHKeyGen = mkList[String] {list=>
            list append "ssh-keygen -t dsa -P '' -f /home/hadoop/.ssh/id_dsa"
          }
          val cmdForSSHByGenKey = mkList[String] {list=>
            list append "cat /home/hadoop/.ssh/id_dsa.pub >> /home/hadoop/.ssh/authorized_keys"
          }
     
          val masterActor = (new SSHActor(masterDns,pem,"hadoop")).start
          masterActor !? BatchExecCmd(cmdForSSHKeyGen)
          val publicKey = (masterActor !? CopyFromRemote("/home/hadoop/.ssh","id_dsa.pub")).asInstanceOf[String]
          actorList append masterActor
          slaveDnsList.map(s=>(new SSHActor(s,pem,"root")).start).foreach(actorList append _)
          
          val sshByGenKeyFutureList = actorList.map(actor => {
            actor !? CopyToRemote(publicKey,"/home/hadoop/.ssh","id_dsa.pub")
            actor !! BatchExecCmd(cmdForSSHByGenKey)
          })
          sshByGenKeyFutureList.foreach(_())
        }
        def putConfFile = execute {actorList =>
          import java.io.File

          var threads = 4

          def createConfStr(template:File, replaceList:List[(String,String)]):String = {
            import scala.io.Source
            @scala.annotation.tailrec
            def replace(str:String, p:List[(String,String)]):String = p match {
              case Nil   => str
              case x::xs => replace(str.replaceAll(x._1,x._2),xs)
            }
            replace(Source.fromFile(template, "utf-8").mkString, replaceList)
          }
          val replaceList = List(
            ("#masterNode#", masterDns),
            ("#slaveNodeList#",slaveDnsList.mkString("\n")),
            ("#threads#",threads.toString),
            ("#tasks#",(slaveDnsList.length*threads).toString)
          )
          val confFileList = new File(templateDir).listFiles.toList.map(template => {
            (template.getName, createConfStr(template, replaceList))
          })
          actorList append (new SSHActor(masterDns,pem,"hadoop")).start
          slaveDnsList.map(s=>(new SSHActor(s,pem,"hadoop")).start).foreach(actorList append _)
          
          val confFileCopyFutureList = actorList.flatMap(actor => {
            confFileList.map(confFile => {
              actor !! CopyToRemote(confFile._2, "/mnt/hadoop", confFile._1)
            })
          })
          confFileCopyFutureList.foreach(_())
        }
        private def execute(f: ListBuffer[Actor]=>Unit):Unit = {
          val a = new ListBuffer[Actor]
          f(a)
          a.foreach(_ ! Exit())
        }
        private def mkList[A](f: ListBuffer[A]=>Unit):List[A] = {
          val l = new ListBuffer[A]
          f(l)
          l.toList
        }
      }
    }
  }
}
