package util

import java.io.File
import javax.inject.{Inject, Singleton}

import org.mapdb.DBMaker
import play.api.Configuration
import play.api.cache.CacheApi

import scala.concurrent.duration.Duration
import scala.reflect.ClassTag

@Singleton
class MapDBCache @Inject() (configuration: Configuration) extends CacheApi {

  val file = new File("db/github.db")

  private val db = DBMaker.newFileDB(file)
    .mmapFileEnable()
    .mmapFileEnableIfSupported()
    .closeOnJvmShutdown()
    .asyncWriteEnable()
    .make()

  override def set(key: String, value: Any, expiration: Duration = Duration.Inf): Unit = {
    db.catPut(key, value)
    db.commit()
  }

  override def get[T: ClassTag](key: String): Option[T] = {
    val data = db.catGet[T](key)
    if(data == null) None else Some(data)
  }

  override def getOrElse[A: ClassTag](key: String, expiration: Duration = Duration.Inf)(orElse: => A): A = {
    get[A](key).getOrElse {
      val value = orElse
      set(key, value, expiration)
      value
    }
  }

  override def remove(key: String): Unit = db.delete(key)
}
