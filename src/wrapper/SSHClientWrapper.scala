package tc.aws {
  import scala.collection.JavaConversions._
  package ssh {
    import java.io.{File,ByteArrayOutputStream}
    import scala.collection.mutable.ListBuffer
    import ch.ethz.ssh2.{Connection,SCPClient}
    class SSHClientWrapper(dest:String, keyFile:File, user:String) {
      def copyToRemote(src:String, remoteDir:String, remoteFileName:String):Unit = {
        connect {
          connection => connection.createSCPClient.put(src.getBytes("utf-8"), remoteFileName, remoteDir)
        }
      }
      def copyFromRemote(remoteDir:String, remoteFileName:String):String = {
        val result = new ListBuffer[String]
        using(new ByteArrayOutputStream) {
          out => {
            connect {
              connection => connection.createSCPClient.get("%s/%s".format(remoteDir,remoteFileName),out)
            }
            result append new String(out.toByteArray, "utf-8")
          }
        }
        result.mkString
      }
      def execCmd(cmd:String) = {
        val result = new ListBuffer[Byte]
        connect {
          connection => using(connection.openSession) {
            session => {
              session.execCommand(cmd)
              val buf = new Array[Byte](1024)
              for(i <- Stream.continually(() => session.getStdout.read(buf)).map(_()).takeWhile(_ != -1)) {
                buf.slice(0,i).map(b => result append b)
              }
            }
          }
        }
        new String(result.toArray, "utf-8")
      }
      private def connect(f: Connection=>Unit):Unit = {
        using(new Connection(dest)) {
          connection => {
            println("connecting to %s...".format(dest))
            connection.connect
            if (connection.authenticateWithPublicKey(user, keyFile, null)) {
              f(connection)
            }
            else {
              throw new Exception
            }
          }
        }
      }
      private def using[A <: {def close():Unit}](r:A)(f: A=>Unit):Unit = {
        try {
          f(r)
        } finally {
          r.close()
        }
      }
    }
  }
}
