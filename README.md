# Android Dualcache

这个库结合了**LRUCache**和**DiskLruCache**两个库，旨在提供一个轻松、容易使用的Android二级缓存服务。
这个库是Fork自 [Vincent Brison](https://github.com/vincentbrison) 的 [Dualcache](https://github.com/vincentbrison/dualcache), 具体使用方法请参照原版文档：
[README](https://github.com/vincentbrison/dualcache/blob/master/README.md)。

# Update
集成方法，修改为使用JitPack集成，具体步骤如下：
1) 增加以下脚本到你的工程根目录的build.gradle文件中
<pre>
allprojects {
    repositories {
        ...
        maven { url 'https://jitpack.io' }
    }
}
</pre>
2）在你的app工程中，增加如下依赖:
<pre>
dependencies {
   compile 'com.github.yuanhoujun:dualcache:v4.0.0'
}
</pre>