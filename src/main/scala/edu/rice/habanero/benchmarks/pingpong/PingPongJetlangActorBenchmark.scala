package edu.rice.habanero.benchmarks.pingpong

import edu.rice.habanero.actors.{JetlangActor, JetlangActorState, JetlangPool}
import edu.rice.habanero.benchmarks.pingpong.PingPongConfig.{Message, PingMessage, StartMessage, StopMessage}
import edu.rice.habanero.benchmarks.{Benchmark, BenchmarkRunner}

/**
 *
 * @author <a href="http://shams.web.rice.edu/">Shams Imam</a> (shams@rice.edu)
 */
object PingPongJetlangActorBenchmark {

  def main(args: Array[String]) {
    BenchmarkRunner.runBenchmark(args, new PingPongJetlangActorBenchmark)
  }

  private final class PingPongJetlangActorBenchmark extends Benchmark {
    def initialize(args: Array[String]) {
      PingPongConfig.parseArgs(args)
    }

    def printArgInfo() {
      PingPongConfig.printArgs()
    }

    def runIteration() {
      val pong = new PongActor()
      val ping = new PingActor(PingPongConfig.N, pong)
      ping.start()
      pong.start()
      ping.send(StartMessage.ONLY)

      JetlangActorState.awaitTermination()
    }

    def cleanupIteration(lastIteration: Boolean, execTimeMillis: Double) {
      if (lastIteration) {
        JetlangPool.shutdown()
      }
    }
  }

  private class PingActor(count: Int, pong: JetlangActor[PingPongConfig.Message]) extends JetlangActor[Message] {

    private var pingsLeft: Int = count

    override def process(msg: PingPongConfig.Message) {
      msg match {
        case _: PingPongConfig.StartMessage =>
          pong.send(new PingPongConfig.SendPingMessage(this))
          pingsLeft = pingsLeft - 1
        case _: PingPongConfig.PingMessage =>
          pong.send(new PingPongConfig.SendPingMessage(this))
          pingsLeft = pingsLeft - 1
        case _: PingPongConfig.SendPongMessage =>
          if (pingsLeft > 0) {
            this.send(PingMessage.ONLY)
          } else {
            pong.send(StopMessage.ONLY)
            exit()
          }
        case message =>
          val ex = new IllegalArgumentException("Unsupported message: " + message)
          ex.printStackTrace(System.err)
      }
    }
  }

  private class PongActor extends JetlangActor[Message] {
    private var pongCount: Int = 0

    override def process(msg: PingPongConfig.Message) {
      msg match {
        case message: PingPongConfig.SendPingMessage =>
          val sender = message.sender.asInstanceOf[JetlangActor[PingPongConfig.Message]]
          sender.send(new PingPongConfig.SendPongMessage(this))
          pongCount = pongCount + 1
        case _: PingPongConfig.StopMessage =>
          exit()
        case message =>
          val ex = new IllegalArgumentException("Unsupported message: " + message)
          ex.printStackTrace(System.err)
      }
    }
  }

}
