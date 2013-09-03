package com.louddoor.tsdcollector

import com.twitter.util._
import com.twitter.conversions.time._
import com.twitter.finagle._
import com.twitter.util.ScheduledThreadPoolTimer
import builder.{ServerBuilder, Server, ClientBuilder}
import com.twitter.server.TwitterServer
import java.net.InetSocketAddress
import org.jboss.netty.channel.socket.nio.NioDatagramChannelFactory
import scala.collection.mutable.{Map => MMap, HashSet, SynchronizedMap, HashMap}

import java.util.concurrent.Executors

case class Metric(name: String, tags: Seq[String])

object StatsCollector extends TwitterServer {
  val flushInterval = flag("flush", 10.seconds, "Flush Interval")
  val aggrFunction = flag("aggr", "sum", "Aggregate Function (sum/avg/max/min) - coming soon")
  val host = flag("host", new InetSocketAddress(4242), "OpenTSDB host")
  val bind = flag("bind", new InetSocketAddress(8084), "Hostname/Port to bind to")

  val pastMetrics = HashSet[Metric]()
  val metrics = HashMap[Metric, Long]().withDefaultValue(0)

  val service = new Service[String, String] {
    def apply(request: String) = {
      log.debug("Incoming request: " + request)

      val lines = request.split("\n")

      val req = lines.map(_.split(" ")).filter(_.length >= 4).map { parts =>
        // parts = put <metric> <timestamp(ignored)> <value> <*tags>
        val value = parts(3)

        (Metric(parts(1), parts.drop(4)), value.toLong)
      }.toMap

      synchronized {
        req.foreach { kv =>
          pastMetrics add kv._1
          metrics(kv._1) += kv._2
        }
      }

      Future.value("")
    }
  }

  def purge(client: Service[String, String])(): Unit = {
    var snapshot: Map[Metric, Long] = Map[Metric, Long]()
    var currentMetrics: Set[Metric] = Set[Metric]()

    synchronized {
      snapshot = metrics.toMap
      currentMetrics = pastMetrics.toSet
      metrics.clear
    }

    if (snapshot.size == 0 && pastMetrics.size == 0)
      return

    log.info("Purging Metrics: " + snapshot.size.toString)

    val timestamp: Long = System.currentTimeMillis / 1000
    val res = currentMetrics.map { metric =>
      val Metric(name, tags) = metric
      val v = snapshot.getOrElse(metric, 0L)

      "put " + name + " " + timestamp.toString + " " + v.toString + " " + tags.mkString(" ")
    }.toSeq.mkString("\n")

    log.info("Purging: " + res)

    client(res + "\n").onSuccess { x =>
      log.info("Purge Response: " + x)
    }.onFailure { e =>
      log.error(e, "Issue with purging")
    }

    Unit
  }


  def main {
    val server: Server = ServerBuilder()
      .codec(StringCodec)
      .bindTo(bind())
      .name("statscollector")
      .build(service)

    val client = ClientBuilder()
      .codec(StringCodec)
      .hosts(host())
      .tcpConnectTimeout(10 seconds)
      .connectTimeout(10 seconds)
      .requestTimeout(10 seconds)
      .timeout(10 seconds)
      .hostConnectionLimit(5)
      .build()

    log.info("Binding to " + bind().toString)
    log.info("OpenTSDB server at " + host().toString)

    val tr = new ScheduledThreadPoolTimer
    val tt = tr.schedule(flushInterval(), flushInterval())(purge(client))
  }
}