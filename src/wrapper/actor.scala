package tc.aws {
  package actor {
    import scala.actors.{Actor,TIMEOUT}
    object Wait {
      def sleep(sec:Int)(f: =>Unit):Unit = {
        println("sleeping for %d sec...".format(sec))
        new WaitActor(sec, f).start
      }
    }
    class WaitActor(sec:Int, f: =>Unit) extends Actor {
      def act() {
        reactWithin(sec*1000) {
          case TIMEOUT => f
        }
      }
    }
  }
}
