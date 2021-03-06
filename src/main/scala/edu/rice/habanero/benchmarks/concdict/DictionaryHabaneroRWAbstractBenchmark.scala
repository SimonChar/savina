package edu.rice.habanero.benchmarks.concdict

import java.util

import edu.rice.habanero.actors.HabaneroActor
import edu.rice.habanero.benchmarks.concdict.DictionaryConfig.{DoWorkMessage, EndWorkMessage, ReadMessage, WriteMessage}
import edu.rice.habanero.benchmarks.{Benchmark, BenchmarkRunner}
import edu.rice.hj.experimental.actors.{ReaderWriterActor, ReaderWriterPolicy}

/**
 *
 * @author <a href="http://shams.web.rice.edu/">Shams Imam</a> (shams@rice.edu)
 */
object DictionaryHabaneroRWAbstractBenchmark {

  abstract class DictionaryHabaneroRWAbstractBenchmark extends Benchmark {
    def initialize(args: Array[String]) {
      DictionaryConfig.parseArgs(args)
    }

    def printArgInfo() {
      DictionaryConfig.printArgs()
    }

    def cleanupIteration(lastIteration: Boolean, execTimeMillis: Double) {
    }
  }

  class Master(numWorkers: Int, numMessagesPerWorker: Int, policy: ReaderWriterPolicy) extends HabaneroActor[AnyRef] {

    private final val workers = new Array[HabaneroActor[AnyRef]](numWorkers)
    private final val dictionary = new Dictionary(DictionaryConfig.DATA_MAP, policy)
    private var numWorkersTerminated: Int = 0

    override def onPostStart() {
      dictionary.start()

      var i: Int = 0
      while (i < numWorkers) {
        workers(i) = new Worker(this, dictionary, i, numMessagesPerWorker)
        workers(i).start()
        workers(i).send(DoWorkMessage.ONLY)
        i += 1
      }
    }

    override def process(msg: AnyRef) {
      if (msg.isInstanceOf[DictionaryConfig.EndWorkMessage]) {
        numWorkersTerminated += 1
        if (numWorkersTerminated == numWorkers) {
          dictionary.sendWriteMessage(EndWorkMessage.ONLY)
          exit()
        }
      }
    }
  }

  private class Worker(master: Master, dictionary: Dictionary, id: Int, numMessagesPerWorker: Int) extends HabaneroActor[AnyRef] {

    private final val writePercent = DictionaryConfig.WRITE_PERCENTAGE
    private var messageCount: Int = 0
    private final val random = new util.Random(id + numMessagesPerWorker + writePercent)

    override def process(msg: AnyRef) {
      messageCount += 1
      if (messageCount <= numMessagesPerWorker) {
        val anInt: Int = random.nextInt(100)
        if (anInt < writePercent) {
          dictionary.sendWriteMessage(new WriteMessage(this, random.nextInt, random.nextInt))
        } else {
          dictionary.sendReadMessage(new ReadMessage(this, random.nextInt))
        }
      } else {
        master.send(EndWorkMessage.ONLY)
        exit()
      }
    }
  }

  private class Dictionary(initialState: util.Map[Integer, Integer], policy: ReaderWriterPolicy) extends ReaderWriterActor[AnyRef](policy) {

    private[concdict] final val dataMap = new util.HashMap[Integer, Integer](initialState)

    override def processWrite(msg: AnyRef) {
      msg match {
        case writeMessage: DictionaryConfig.WriteMessage =>
          val key = writeMessage.key
          val value = writeMessage.value
          dataMap.put(key, value)
          val sender = writeMessage.sender.asInstanceOf[HabaneroActor[AnyRef]]
          sender.send(new DictionaryConfig.ResultMessage(this, value))
        case _: DictionaryConfig.EndWorkMessage =>
          printf(BenchmarkRunner.argOutputFormat, "Dictionary Size", dataMap.size)
          exit()
        case _ =>
          System.err.println("Unsupported message: " + msg)
      }
    }

    override def processRead(msg: AnyRef) {
      msg match {
        case readMessage: DictionaryConfig.ReadMessage =>
          val value = dataMap.get(readMessage.key)
          val sender = readMessage.sender.asInstanceOf[HabaneroActor[AnyRef]]
          sender.send(new DictionaryConfig.ResultMessage(this, value))
        case _ =>
          System.err.println("Unsupported message: " + msg)
      }
    }
  }

}
