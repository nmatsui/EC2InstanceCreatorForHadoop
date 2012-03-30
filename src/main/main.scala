package tc.aws {
  package main {
    import tc.aws.actor._
    import tc.aws.message._
    import tc.aws.dto._
    object ShowAMIList {
      def main(args:Array[String]):Unit = args match {
        case x if args.length==1 => execute(args.tail)
        case _                   => println("Usage: %s".format(args(0)))
      }
      private def execute(args:Array[String]):Unit = {
        println("--ShowAMIList JOB start--")
        val amiList = ((new EC2Actor).start !? AmiList()).asInstanceOf[List[AMI]]
        println("<<amiList>>")
        amiList.foreach(ami => {
          println("%s(%s %s)".format(ami.imageId, ami.architecture, ami.location))
          ami.tags.foreach(tag => {
            println("  %s : %s".format(tag._1, tag._2))
          })
        })
        println("--ShowAMIList JOB complete--")
      }
    }
    object ShowInstanceList {
      def main(args:Array[String]):Unit = args match {
        case x if args.length==1 => execute(args.tail)
        case _                   => println("Usage: %s".format(args(0)))
      }
      private def execute(args:Array[String]):Unit = {
        println("--ShowInstanceList JOB start--")
        val instanceInfoList = ((new EC2Actor).start !? AllInstanceInfo()).asInstanceOf[List[VirtualMachine]]
        println("<<instanceList>>")
        instanceInfoList.length match {
          case 0 => println("No instance was found")
          case _ => { 
            instanceInfoList.foreach(vm => {
              println("%s [%s]<%s> (%s) %s".format(vm.instanceId, vm.publicDnsName, vm.privateDnsName, vm.imageId, vm.status))
            })
          }
        }
        println("--ShowInstanceList JOB complete--")
      }
    }
    object CreateHadoopVM {
      def main(args:Array[String]):Unit = {
        args.length match {
          case i if i==8 => execute(args.tail)
          case _         => println("Usage: %s numOfSlaves".format(args(0)))
        }
      }
      private def execute(args:Array[String]):Unit = {
        println("--CreateHadoopVM(num of slaves : %d) JOB start--".format(args(6).toInt))
        val (master, slaves) = runInstance(args)
        def printVM(vm:VirtualMachine) = {
          val fm = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss'-'")
          println("%s [%s] (%s %s)".format(vm.instanceId, vm.publicDnsName, vm.imageId, fm.format(vm.launchTime)))
        }
        try {
          configureInstances(args, master, slaves)
        
          println("<<masterNode>>")
          printVM(master)
          println("<<slaveNode>>")
          slaves.foreach(slave => printVM(slave))
          println("--CreateHadoopVM JOB complete--")
        }
        catch {
          case e:Exception => {
            println("Something wrong with ssh connection")
          }
        }            
      }
      private def runInstance(args:Array[String]):(VirtualMachine, List[VirtualMachine]) = {
        val ami = args(0)
        val instanceType = args(1)
        val group = args(2)
        val pemName = args(3)
        val numOfSlaves = args(6).toInt

        val masterFuture = (new EC2Actor).start !! RunInstance(ami, instanceType, group, pemName, 1)
        val slavesFuture = (new EC2Actor).start !! RunInstance(ami, instanceType, group, pemName, numOfSlaves)

        val master = masterFuture().asInstanceOf[List[VirtualMachine]](0)
        val slaves = slavesFuture().asInstanceOf[List[VirtualMachine]]
        println("--complete runInstance--")
        (master, slaves)
      }
      private def configureInstances(args:Array[String], master:VirtualMachine, slaves:List[VirtualMachine]):Unit = {
        val masterDns = master.publicDnsName
        val slaveDnsList = slaves.map(_.publicDnsName)
        val pem = args(4)
        val templateDir = args(5)
    
        val executer = new SSHExecuter(masterDns, slaveDnsList, pem, templateDir)
        executer.mkHadoopDir
        executer.setHadoopSSHAuthByPem
        executer.setHadoopSSHAuthByGenKey
        executer.putConfFile
        println("--complete configureInstance--")
      }
      private class SSHExecuter(masterDns:String, slaveDnsList:List[String], pem:String, templateDir:String) {
        import scala.actors.Actor
        import scala.collection.mutable.ListBuffer

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
            list append "rm /home/hadoop/.ssh/id_dsa"
            list append "rm /home/hadoop/.ssh/id_dsa.pub"
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
              actor !! CopyToRemote(confFile._2, "/usr/local/hadoop/conf", confFile._1)
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
