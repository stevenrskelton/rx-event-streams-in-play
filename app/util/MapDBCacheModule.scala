package util

import javax.inject.{Inject, Provider, Singleton}

import play.api.cache.{CacheApi, Cached, NamedCache}
import play.api.inject.{BindingKey, Injector, Module}
import play.api.{Configuration, Environment}
import play.cache.{CacheApi => JavaCacheApi, DefaultCacheApi => DefaultJavaCacheApi, NamedCacheImpl}

@Singleton
class MapDBCacheApiProvider(namespace: String, client: BindingKey[MapDBCache], configuration: Configuration) extends Provider[MapDBCache] {
  @Inject private var injector: Injector = _

  lazy val get: MapDBCache = {
    new MapDBCache(configuration)
  }
}

class JavaCacheApiProvider(key: BindingKey[CacheApi]) extends Provider[JavaCacheApi] {
  @Inject private var injector: Injector = _
  lazy val get: JavaCacheApi = {
    new DefaultJavaCacheApi(injector.instanceOf(key))
  }
}

class CachedProvider(key: BindingKey[CacheApi]) extends Provider[Cached] {
  @Inject private var injector: Injector = _
  lazy val get: Cached = {
    new Cached(injector.instanceOf(key))
  }
}

@Singleton
class MapDBCacheModule extends Module {

  def bindings(environment: Environment, configuration: Configuration) = {
    val defaultCacheName = "default" //configuration.underlying.getString("play.modules.cache.defaultCache")
    //val bindCaches = configuration.underlying.getStringList("play.modules.cache.bindCaches").toSeq

    // Creates a named cache qualifier
    def named(name: String): NamedCache = {
      new NamedCacheImpl(name)
    }

    // bind a cache with the given name
    def bindCache(name: String) = {
      val namedCache = named(name)
      val cacheApiKey = bind[CacheApi].qualifiedWith(namedCache)
      Seq(
        cacheApiKey.to(new MapDBCacheApiProvider(name, bind[MapDBCache], configuration)),
        bind[JavaCacheApi].qualifiedWith(namedCache).to(new JavaCacheApiProvider(cacheApiKey)),
        bind[Cached].qualifiedWith(namedCache).to(new CachedProvider(cacheApiKey))
      )
    }

    Seq(
      // alias the default cache to the unqualified implementation
      bind[CacheApi].to(bind[CacheApi].qualifiedWith(named(defaultCacheName))),
      bind[JavaCacheApi].to[DefaultJavaCacheApi]
    ) ++ bindCache(defaultCacheName) /*++ bindCaches.flatMap(bindCache)*/
  }
}
