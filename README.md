一、Netty

Netty 与 JDK 原生 NIO 相比，提供了非常简单易用的 API。

1. 主要优点

- API 使用简单 - 开发门槛低
- 功能强大 - 预制了多种编码功能，支持多种主流协议
- 定制能力强 - 可以通过 ChannelHandler 对通信框架进行灵活扩展
- 性能高 - 与其他业界主流的 NIO 框架对比，Netty 的综合性能最优
- 成熟、稳定 - Netty 修复了已经发现的所有 JDK NIO 中的 BUG，业务开发人员不需要再为 NIO 的 BUG 而烦恼
- 社区活跃 - 版本迭代周期短，发现的 BUG 可以被及时修复

2. IO底层原理

用户程序进行 IO 读写，依赖于底层的 IO 读写。

需要注意的是，上层应用的IO操作实际上并不是物理设别级别的读写，而是缓存的复制：读取操作并不是直接从物理设备把数据读取到内存中，而是把数据从内核缓冲区复制到进程缓冲区（用户缓冲区）；而写操作也不是直接把数据写入物理设备，而是把数据从进程缓冲区复制到内核缓冲区，而底层的读写交换，由操作系统内核完成

内核缓冲区位于操作系统的内核空间，而进程缓冲区位于用户空间。

缓冲区

缓冲区的设计的目的，是为了避免频繁地与设备之间的物理交换。底层操作会对内核缓冲区进行监控，等待缓冲区达到一定数量后，集中执行物理设备的实际IO操作

完成一次Java服务端的socket请求与响应流程

- 客户端请求 - Linux通过网卡读取客户端请求，将请求数据读取到内核缓冲区
- 获取请求数据 - Java进程从Linux内核缓冲区赋值数据到Java进程缓冲区
- Java进程处理服务 - Java进程在自己的用户空间处理客户端的请求
- 返回数据 - Java进程处理完成后，构建响应数据并从用户缓冲区写入内核缓冲区
- 响应 - Linux内核通过网络IO，将内核缓冲区中的数据写入网卡，网卡通过底层通信协议，将数据响应给客户

阻塞与非阻塞的区别？同步与异步的区别？

阻塞非阻塞 - 是用户空间程序的执行状态。

阻塞 需要内核IO彻底完成后，才返回到用户空间执行用户的操作，此过程一直是阻塞状态(此过程不需要消耗CPU资源，但基本一个请求就需要占用一个线程，不适合高并发场景)

非阻塞 这里分两种情况，如果用户进程请求的数据在内核缓冲区中存在，是阻塞的，会直到数据从内核缓冲区复制到用户缓冲区后返回；如果没有数据，调用会立刻返回，返回一个调用失败信息，然后需要用户进程不停调用（需要额外的CPU资源，但一个线程可以干更多事，需要注意的是Java的NIO不完全是同步非阻塞IO模型，虽然两种模型的英文缩写一样，Java中NIO模型是IO多路复用模型）

同步异步 - 是一种用户空间与内核空间的IO发起方式。

同步IO 是指用户空间的线程是主动发起IO请求的一方，内核空间是被动接受方。

异步IO 指系统内核是主动发起IO请求的一方，用户空间的线程是被动接受方，有点类似Java的回调模式，用户空间线程向内核空间注册IO事件的回调函数，由内核去主动调用。（需要内核提供支持，目前Windows通过IOCP实现真正异步IO，但Linux异步IO不完善，其底层是epoll模型，与IO多路复用相同）

3. IO多路复用模型

IO多路复用模型最大的优点就是避免了同步非阻塞IO模型中轮询等待的问题。

指一个进程/线程可以同时监视多个文件描述符（一个网络连接，操作系统底层使用一个文件描述符表示），一旦其中一个或多个文件可读或者可写，操作系统内核就通知该进程/线程。

目前支持IO多路复用的系统调用，有select、epoll等，select系统调用，几乎在所有的操作系统上都有支持，具有良好的跨平台特性，epoll是在Linux 2.6内核中提出的，是select系统调用的Linux增强版本

IO多路复用模型的一次请求流程如下：

1. 选择器注册 - 将客户端的请求的目标socket网络连接注册到选择器中，Java中对应的选择器类是Selector类
2. 就绪状态的轮询 - 选择器轮询注册过的所有socket连接的就绪状态，内核会返回一个就绪的socket列表，如果和注册的socket匹配就可以将就绪的状态添加到一个选择器的集合（选择键集合）中
3. 根据选择键集合执行实际的IO事件

4. 修改Linux支持的最大句柄数

为了支持更多的网络连接并发，需要修改Linux操作系统的支持的最大句柄数。

文件句柄，也叫文件描述符，是内核为了高效管理已被打开的文件所创建的索引，它是一个非负整数，用来指代被打开的文件。

如果文件句柄数不够，系统就会报Can't open so many files的错误。

可以通过以下命令修改Linux最大句柄数(默认是1024)

    ulimit -n 1000000

但这个命令是临时的，如果连接中断，用户退出，数值又会变回1024，所以需要修改开机启动文件/etc/rc.local

    ulimit -SHn 1000000

添加以上内容，-S表示软性极限值，超过这个极限值，内核会发出警告，-H是硬性极限值

但是普通用户无法修改软性极限值，可以通过编辑/etc/security/limits.conf，加入以下内容

    soft nofile 1000000
    hard nofile 1000000

在使用分布式搜索引擎ES的时候就必须修改这个文件

5. Java NIO

Java NIO 由以下三个核心组件组成：

- Channel（通道）
  在 JDK1.4 之前的OIO中，同一个网络连接会关联到两个流：一个输入流，一个输出流。通过这两个流，不断进行输入输出操作。NIO中，同一个网络连接使用一个通道表示，既可以从通道读，也可以从通道写
- Buffer（缓冲区）
  与通道直接交互的就是缓冲区，是OIO中没有的概念
- Selector（选择器）
  是一个IO事件的查询器，通过选择器，一个线程就可以查询多个通道的IO事件的就绪状态

5.1 缓冲区Buffer

NIO的Buffer类（缓冲区）是一个抽象类，位于java.nio包中，其本质是一个内存块(数组)，但它提供了一组更加有效的方法，用来进行写入和读取的交替访问。

需要强调的是 Buffer 是一个非线程安全的类

Buffer抽象类的子类有ByteBuffer(最常用)、CharBuffer、DoubleBuffer、FloatBuffer、IntBuffer、LongBuffer、ShortBuffer、MappedByteBuffer

Buffer的三个主要属性

- capacity容量 - 一经初始化无法修改，存储单位是Buffer子类型的单位
- position读写位置 - 初始是0，每写入一个+1，调用flip翻转方法切换为读模式时，position置0
- limit读写长度限制 - 写入时，默认值和capacity相同，最大能写入的position为limit-1，调用flip方法后limit的值为翻转前position的值

主要方法

- allocate()创建缓冲区
  static IntBuffer intBuffer = IntBuffer.allocate(20); //分配了20*4个字节的空间,position:0;capacity,limit:20
- put()写入缓冲区
  intBuffer.put(1); //position:1
- flip()翻转
  intBuffer.flip(); //position:0;capacity:20;limit:1，要重新切换回写方法，可以使用clear()清空 或 compact()压缩方法，缓冲区是可以重复写的
- get()读缓冲区
  int a = intBuffer.get();  //position+1，当positon和limit相等时再执行get()会报错
- rewind()倒带
  可以重新读已经读完的缓冲区，即position置0，并清除mark标记
- mark()与reset()
  mark将当前position值保存起来，reset将postion重新设为mark的值
- clear()清空缓冲区
  缓冲区切为写模式，postion置空，limit赋为capacity的值

5.2 通道Channel

NIO的一个连接可以用一个Channel来表示

Java Channel中最重要的四种通道：FileChannel、SocketChannel、ServerSocketChannel、DatagramChannel

- FileChannel文件通道
  用于文件的数据读写，是阻塞模式，不能设置为非阻塞模式
  - 获取通道
    可以根据输入、输出流获取通道
    fileInputStream.getChannel();
    也可以通过文件随机访问类，获取通道
    RandomAccessFile file = new RandomAccessFile ([filename.txt],[rw]);
    FileChannel channel = file.getChannel();
  - 读取通道
    通过int read(ByteBuffer buf);方法从通道读取数据，并写入到缓冲区中
    ByteBuffer byteBuffer = ByteBuffer.allocate(20);
    int length = -1;
    while((length = channel.read(buf)) != -1){
    //处理读取到的buf的数据
    }
  - 写入通道
    通过int write(ByteBuffer buf);方法从缓冲区读取数据，并写入到通道中
    buffer.flip();//翻转缓冲区为读模式
    int outLength = 0;
    while((outLength = outchannel.write(buffer)) != 0){
    sout(写入的字节数：outLength);
    }
  - 关闭通道
    channel.close();
  - 强制刷新到磁盘
    在将缓冲区数据写入通道时，处于性能原因，操作系统不可能实时将通道数据写入文件，可以通过force()方法将数据强制写入文件
    channel.force(true);
- SocketChannel套接字通道
  用于Socket套接字TCP连接的数据读写
- ServerSocketChannel服务器嵌套字通道
  允许我们监听TCP连接请求，为每个监听到的请求，创建一个SocketChannel套接字通道
  ServerSocketChannel应用于服务器端，而SocketChannel同时处于服务器端和客户端，两种通道都支持阻塞或非阻塞的配置通过configureBlocking方法，true为阻塞，false为非阻塞
  - 获取SocketChannel通道
    在客户端，通过socketChannel对服务器ip,端口发起连接：
    SocketChannel socketChannnel = SocketChannel.open();
    socketChannel.configureBlocking(false); //非阻塞方式
    socketChannel.connet(new InetSocketAddress([127.0.0.1],80));
    while(! socketChannel.finishConnect()){//因为是异步所以需要轮询检查完成情况
    }
    在服务端，通过ServerSocketChannel监听获取SocketChannel：
    ServerSocketChannel server = (ServerSocketChannel) key.channel();//通过事件获取监听通道
    SocketChannel socketchannel = server.accept();
    socketchannel.configureBlocking(false);
  - 读取SocketChannel
    和文件通道读取访问一样
    ByteBuffer buf = ByteBuffer.allocate(1024);
    int bytesRead = socketChannel.read(buf);
    但和文件通道不一样的是，socket通道是异步的，所以需要根据返回值(读取的字节数)判断是否读到数据，如果返回的是-1表示通道数据读完，可以关闭通道了。这样非常复杂，所以就需要Selector选择器
  - 写入SocketChannel
    buffer.flip();
    socketChannel.write(buffer);
  - 关闭SocketChannel
    在关闭前，如果传输通道用来写入数据，则先调shutdownOutput()终止输出方法，然后调用socketChannel.close()方法，关闭套接字连接
    socketChannel.shutdownOutput();
    IOUtil.closeQuiely(socketChannel);
- DatagramChannel数据报通道
  用于UDP协议的读写，和Socket套接字不同在于，UDP不是面向连接的协议，所以只要知道服务器的ip和端口，就可以发送数据
  - 获取通道
    DatagramChannel channel = DatagramChannel.open();
    channel.configureBlocking(false);
  - 绑定数据报监听端口
    channel.socket().bind(new InetSocketAddress(10222));
  - 读取通道
    ByteBuffer buf = ByteBuffer.allocate(1024);
    SocketAddress clientAddr = channel.receive(buf);//返回值是发送端的ip和端口
    非阻塞模式下需要选择器确认什么时候可读
  - 写入通道
    buffer.flip();
    sendChannel.send(buffer,new InetAddress(ip,port));
    buffer.clear();
  - 关闭通道
    channel.close();

5.3 选择器Selector

选择器可以监控多个通道的IO状态，并把状态达成的事件放入SelelctKey集合中（可以从SelectKey中获取IO事件类型、IO事件发生的通道、绑定的附件等）。

那么通道如何注册到选择器上呢?

channel.register(Selector sel , int ops)

第一个参数是要注册到的选择器，第二个参数指定选择器要监控的IO事件类型

可供选择器监控的通道IO事件类型，包括以下四种

- 可读SelectionKey.OP_READ：通道有数据可读状态
- 可写SelectionKey.OP_WRITE：一个等待写入数据的通道状态
- 连接SelectionKey.OP_CONNECT：SocketChannel完成了和对端的连接状态
- 接收SelectionKey.OP_ACCEPT：ServerSocketChannel监听到了一个新连接的到来

如果要监控多个事件，使用按位或的方式实现SelectionKey.OP_READ|SelectionKey.OP_WRITE

所有通道类型都可以被选择器监控吗？

并不是，只有继承了抽象类SelectableChannel的才可以被监控

以下为选择器的使用流程：

1. 获取选择器实例
   Selector selector = Selector.open();
2. 将通道注册到选择器实例
   ServerSocketChannel channel = ServerSocketChannel.open();
   channnel.configureBlocking(false);//需要注意，注册到选择器的通道必须是非阻塞模式
   channnel.bind(new InetAddress(port));
   channel.register(selector,SelectionKey.OP_ACCEPT);
3. 选出感兴趣的IO就绪事件（选择键集合）
   while(selector.select() > 0 ){//阻塞调用，一直到至少有一个通道发生了注册IO事件
   Iterator keyIterator = selector.selectedKeys().iterator();
   while(keyIterator.hasNext()){
   SelectionKey key = keyIterator.next();
   if(key.isAcceptable()){//通道处于可接收状态，执行注册的IO事件
   }else if(key.isConnectable()){//通道处于连接状态，执行注册的IO事件
   }else if(key.isReadable()){//通道处于可读状态，执行注册的IO事件
   }else if(key.isWritable()){//通道处于可写状态，执行注册的IO事件
   }
   keyIterator.remove();//处理完成后移除选择键
   }}

6. Reactor反应器模式

Reactor反应器模式是高性能网络编程在设计和架构层面的基础模式。其在Nginx、Redis、Netty中都有应用。

6.1 定义及优缺点

反应器模式由Reactor反应器线程、Handlers处理器两大角色组成

- Reactor反应器线程的职责：负责响应IO事件，并且分发到Handlers处理器
- Handlers负责非阻塞的执行业务逻辑

优点

1. 响应快 - 虽然同一反应器线程本身是同步的，但不会被单个连接的同步IO阻塞
2. 编程相对简单 - 最大程度避免了多线程同步数据问题，也避免了多线程的各个进程切换的开销
3. 可拓展，可以方便地通过增加反应器线程的个数来充分利用CPU资源

缺点

1. 反应器模式增加了一定的复杂性，因而有一定的门槛，而不易于调试
2. 反应器模式需要操作系统底层的IO多路复用的支持，如Linux中的epoll。如果操作系统的底层不支持IO多路复用，反应器模式不会那么高效

6.2 多线程OIO

最初的网络服务器程序使用以下逻辑：

    while(true){
    socket = accept();//阻塞，接收线程
    handle(socket);//业务逻辑
    }

最大的问题是：如果前一个handle没有处理完，后面的连接请求就无法接收，影响服务器吞吐量

于是出现了经典的Connection Per Thread（一个线程处理一个连接）模式

一个线程轮询接收任务，接收到任务后创建一个执行线程异步执行

早期的Tomcat服务器就是这样实现的，但同样存在一个问题，对于大量的连接，需要耗费大量的线程资源。

解决方式是使用Reactor反应器模式，对线程数量进行控制，做到一个线程处理大量的连接

6.3 单线程Reactor反应器模式

Reactor反应器模式类似事件驱动模型

在事件驱动模式中，当有事件触发时，事件源会将事件dispatch分发到handle处理器进行事件处理。反应器中的反应器角色，类似事件驱动模式中 dispatcher 事件分发器角色。

- Reactor反应器：负责查询IO事件，当检测到一个IO事件，将其发送给相应的Handler处理器去处理。这里的IO事件，就是NIO中选择器监控的通道IO事件
- Handler处理器：与IO事件绑定，负责IO事件的处理。完成真正的连接建立、通道的读取、处理业务逻辑、负责将结果写出到通道等

而单线程Reactor反应器模式就是反应器和处理器在一个线程中执行，那么是如何实现的，主要基于以下方法：

- void attach(Object o)
  此方法可以把任何java对象作为附件添加到SelectionKey中，当现场Reactor需要将Handler处理器实例，作为附件添加到SelectionKey中
- Object attachment()
  此方法是取出SelectionKey的附件

所以单线程Reactor反应器实现如下

1. 注册通道到选择器上后(register方法有返回值SelectionKey)，根据返回值绑定Handler实例(一个Runable实现类)
2. 反应器轮询拿到触发了事件的SelectionKey，再分发给绑定在SelectionKey上的Handler业务处理器，并执行

最后通过一个Reactor反应器项目(回显服务器)来完整的了解单线程的Reactor反应器模式

EchoServer回显服务器的功能很简单：读取客户端的输入，回显到客户端，所以也叫回显服务器。

这里主要涉及了3个重要的类：

1. 反应器类：EchoServerReactor类
2. 两个处理器类：AcceptorHandler 新连接处理器、EchoHandler回显处理器

反应器类EchoServerReactor:

    public class SingleReactor implements Runnable {
    
        Selector selector;
        ServerSocketChannel serverSocket;
    
        public SingleReactor() throws IOException {
            //...获取选择器、开启 serverSocket 服务监听通道
            this.selector = Selector.open();
            this.serverSocket = ServerSocketChannel.open();
            //需要注意，注册到选择器的通道必须是非阻塞模式
            serverSocket.configureBlocking(false);
            serverSocket.bind(new InetSocketAddress(10002));
            SelectionKey sk = serverSocket.register(selector, SelectionKey.OP_ACCEPT);
            //...绑定AcceptorHandler 新连接处理器到selectKey
            sk.attach(new AcceptorHandler());
        }
    
        /**
         * 轮询和分发事件
         */
        @Override
        public void run() {
            try {
                //线程不被中断就一直轮询
                while (!Thread.interrupted()) {
                    selector.select();
                    Set<SelectionKey> selected = selector.selectedKeys();
                    for (SelectionKey sk : selected) {
                        //反应器负责 dispatch 收到的事件
                        dispatch(sk);
                    }
                    selected.clear();
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    
        void dispatch(SelectionKey sk) {
            Runnable handler = (Runnable) sk.attachment();
            //调用之前 attach 绑定到选择键的 handler 处理器对象
            if (handler != null) {
                handler.run();//因为是run不是start所以不会创建新线程
            }
        }
    
        /**
         * Handler:新连接处理器
         */
        class AcceptorHandler implements Runnable {
    
            @Override
            public void run() {
                try {
                    SocketChannel channel = serverSocket.accept();
                    if (channel != null) {
                        new SingleReactorHandler(selector, channel);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        
        public static void main(String[] args) throws IOException{
              new Thread(new SingleReactor()).start();
        }
    }

EchoHandler 回显处理器，主要是完成客户端的内容读取和回显，具体如下：

    @Slf4j
    public class SingleReactorHandler implements Runnable {
        final SocketChannel channel;
        final SelectionKey sk;
        final ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
        static final int RECEVING = 0, SENDING = 1;
        int state = RECEVING;
    
        public SingleReactorHandler(Selector selector, SocketChannel c) throws IOException {
            this.channel = c;
            c.configureBlocking(false);
            //获取选择键，再设置感兴趣的IO事件，这里注册到和反应器同一个选择器中
            this.sk = channel.register(selector, RECEVING);
            //将 Handler 自身作为选择器的附件
            sk.attach(this);
            //注册Read就绪事件
            sk.interestOps(SelectionKey.OP_READ);
            selector.wakeup();
        }
    
        @Override
        public void run() {
            try {
                if (state == SENDING) {
                    //写入通道
                    channel.write(byteBuffer);
                    //写完后，准备开始从通道读，byteBuffer切换成写入模式
                    byteBuffer.clear();
                    //写完后，切换为接收的状态
                    state = RECEVING;
                } else if (state == RECEVING) {
                    //从通道读
                    int length = 0;
                    while ((length = channel.read(byteBuffer)) > 0) {
                        log.info(new String(byteBuffer.array(), 0, length));
                    }
                    //读完后，准备开始写入通道，buteBuffer 切换成读取模式
                    byteBuffer.flip();
                    //注册write就绪事件
                    sk.interestOps(SelectionKey.OP_WRITE);
                    //读完后，进入发送状态
                    state = SENDING;
                }
                //处理结束后不能关闭selectKey，需要重复使用
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

总结

单线程Reactor反应器模式，是基于Java的NIO实现的。相较于传统的多线程OIO，反应器模式不再需要启动成千上万条线程。

但同样有问题，当其中某个Handler阻塞时，会导致其他所有Handler得不到执行，当然AcceptorHandler(新连接处理器)也被阻塞，导致服务不能接受到新连接，因此单线程反应器模型用的比较少

6.4 多线程的Reactor反应器模式

多线程池Reactor反应器的演进，分为两个方面：

1. 升级Handler处理器。既要使用多线程，又要尽可能高效率，采用线程池
2. 升级Reactor反应器。引入多个Selector选择器(即多个子反应器，每个反应器负责一个选择器)，提升大量通道的能力

下面看一下反应器代码：

    class MultiThreadEchoServerReactor { 
        ServerSocketChannel serverSocket;
        AtomicInteger next = new AtomicInteger(0);
        //选择器集合，引入多个选择器
        Selector[] selectors = new Selector[2];
        //引入多个子反应器
        SubReactor[] subReactors = null;
    
        public MultiThreadReactor() throws IOException {
            //初始化多个选择器
            selectors[0] = Selector.open();
            selectors[1] = Selector.open();
            serverSocket = ServerSocketChannel.open();
            InetSocketAddress address = new InetSocketAddress("127.0.0.1", 10003);
            serverSocket.socket().bind(address);
            //非阻塞
            serverSocket.configureBlocking(false);
            //第一个选择器，负责监控新连接事件
            SelectionKey sk = serverSocket.register(selectors[0], SelectionKey.OP_ACCEPT);
            //绑定Handler: attach 新连接监控 handler 处理器到 SelectionKey（选择键）
            sk.attach(new AcceptorHandler());
            //第一个子反应器，对应一个选择器
            SubReactor subReactorl = new SubReactor(selectors[0]);
            SubReactor subReactor2 = new SubReactor(selectors[1]);
            this.subReactors = new SubReactor[]{subReactorl, subReactor2};
        }
    
        public void startService() {
            //一个子反应器对应一个线程
            new Thread(subReactors[0]).start();
            new Thread(subReactors[1]).start();
        }
    
        //子反应器
        public class SubReactor implements Runnable {
            //每个子反应器负责一个选择器的查询和选择
            final Selector selector;
    
            public SubReactor(Selector selector) {
                this.selector = selector;
            }
    
            @Override
            public void run() {
                try {
                    while (!Thread.interrupted()) {
                        selector.select();
                        Set<SelectionKey> keySet = selector.selectedKeys();
                        for (SelectionKey sk : keySet) {
                            //反应器负责dispatch收到的事件
                            dispatch(sk);
                        }
                    }
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
    
            void dispatch(SelectionKey sk) {
                Runnable handler = (Runnable) sk.attachment();
                if (handler != null) {
                    handler.run();
                }
            }
    
        }
    
        //Handler：新连接处理器
        public class AcceptorHandler implements Runnable {
            @Override
            public void run() {
                try {
                    SocketChannel channel = serverSocket.accept();
                    if (channel != null) {
                        new MultiThreadEchoHandler(selectors[next.get()], channel);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                if (next.incrementAndGet() == selectors.length) {//如果原子整数自增到选择器长度置0
                    next.set(0);
                }
            }
        }
        public static void main(String[] args) throws IOException{
            MultiThreadEchoServerReactor server = new MultiThreadEchoServerReactor();
            server.startService();
        }
    }

执行器的代码：

    @Slf4j
    public class MultiThreadEchoHandler implements Runnable {
    
        final SocketChannel channel;
        final SelectionKey sk;
        final ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
        static final int RECEIVING = 0, SENDING = 1;
        int state = RECEIVING;
        //引入线程池
        static ExecutorService pool = Executors.newFixedThreadPool(4);
    
        public MultiThreadEchoHandler(Selector selector, SocketChannel c) throws IOException {
            this.channel = c;
            c.configureBlocking(false);
            //将通道注册到选择器上
            this.sk = channel.register(selector, RECEIVING);
            //将本Handler绑定到选择键上
            sk.attach(this);
            //向sk选择键注册Read就绪事件
            sk.interestOps(SelectionKey.OP_READ);
            selector.wakeup();
        }
    
        @Override
        public void run() {
            //异步任务，在独立的线程池中执行
            pool.execute(new AsyncTask());
        }
    
        class AsyncTask implements Runnable {
            @Override
            public void run() {
                MultiThreadEchoHandler.this.asyncRun();
            }
        }
    
        public synchronized void asyncRun() {
            try {
                if (state == SENDING) {
                    //写入通道
                    channel.write(byteBuffer);
                    //写完后，准备开始从通道读，byteBuffer切换成写入模式
                    byteBuffer.clear();
                    //写完后，切换为接收的状态
                    state = RECEIVING;
                } else if (state == RECEIVING) {
                    //从通道读
                    int length = 0;
                    while ((length = channel.read(byteBuffer)) > 0) {
                        log.info(new String(byteBuffer.array(), 0, length));
                    }
                    //读完后，准备开始写入通道，buteBuffer 切换成读取模式
                    byteBuffer.flip();
                    //注册write就绪事件
                    sk.interestOps(SelectionKey.OP_WRITE);
                    //读完后，进入发送状态
                    state = SENDING;
                }
                //处理结束后不能关闭selectKey，需要重复使用
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

7. 异步回调模式

Netty中大量使用了异步回调技术，并且基于Java的异步回调，设计了自己的一套异步回调接口和实现

7.1 FutureTask

要实现异步回调。我们可以使用join异步阻塞方法，但join有一个问题：被合并的线程没有返回值。

所以为了获得异步线程的返回结果，JDK1.5 后提供了 FutureTask 方式。其最重要的是FutureTask类和Callable接口。

Callable

Callable是一个函数式接口，其唯一的抽象方法 call 有返回值

Java中的线程类，只有一个Thread类，如果 Callable 实例需要异步执行，就要转为Thread类，但Thread类构造方法参数类型是Runnable类型。为此，Java 提供了在 Callable 实例和 Thread 的构造参数之间的一个搭桥类——FutureTask类。

FutureTask

FutureTask 构造函数的参数为Callable类型，实际上是对Callable的二次封装，FutureTask间接继承了Runnable接口，从而可以作为Thread的构造参数使用。

在Java语言中，将FutureTask类的一系列操作，抽象出来作为一个重要接口——Future接口。

Future接口主要提供了以下抽象方法

V get(); 获取并发执行结果，这个方法是阻塞的

boolean isDone(); 判断并发任务是否执行完成

boolean cancel(boolean mayInterruptRunning); 取消并发任务的执行

而FutureTask实现了Future接口，提供了外部操作异步任务的能力。

FutureTask内部有一个Runnable的run方法，会执行callable的call方法，然后将结果保存到成员变量outcome中，可供FutureTask类的结果获取方法get()获取。

示例如下：

    static class WashJob implements Callable<Boolean>{
        @Override
        public Boolean call() throws Exception{
            try{
                Logger.info("洗碗");
            }catch(Exception e){
                return false;
            }
            return true;
        }
    }
    public static void main(String args[]){
        Callable<Boolean> job = new WashJob();
        FutureTask<Boolean> task = new FutureTask<>(job);//将Callable函数接口保存为成员变量，在run中执行
        Thread thread = new Thread(task);//因为Callable也实现了Runnable接口
        thread.start();
        boolean result = task.get();//阻塞拿到执行结果
    }
    

但原生的 Java Api 没有提供阻塞以外的方法获取返回结果，被阻塞的线程不能干任何事，所以Guava框架提供了非阻塞获取结果的方式

7.2 Guava的异步回调

Guava 是谷歌公司提供的 Java 扩展包。为了实现非阻塞的获取异步线程结果，Guava对Java的异步回调，做了以下增强：

- 引入了一个新接口 ListenableFuture 
  继承了Java的 Future 接口，使得Future异步任务，在Guava中能被监控和获取非阻塞执行结果
- 引入了一个新接口 FutureCallback
  这是一个独立的新接口。该接口的目的，是在异步执行完任务后，根据异步结果，完成不同的回调处理，并且可以处理异步结果。
  它提供了两个回调方法：onSuccess，参数是任务执行结果；onFailure，参数是发送的异常。

示例如下：

    static class WashJob implements Callable<Boolean>{
        @Override
        public Boolean call() throws Exception{
            try{
                Logger.info("洗碗");
            }catch(Exception e){
                return false;
            }
            return true;
        }
    }
    public static void main(String args[]){
        Callable<Boolean> job = new WashJob();
        //创建java线程池
        ExecutorService pool = Executors.newFixedThreadPool(10);
        //包装java线程池，构造Guava线程池
        ListeningExecutorService gPool = MoreExecutors.listeningDecorator(pool);
        //提交任务到Guava线程池，获取异步任务
        ListenableFuture<Boolean> future = gPool.submit(job);
        //绑定异步回调方法
        Futures.addCallback(future，new FutureCallback<Boolean>(){
            public void onSuccess(Boolean r){
                //处理结果r
            }
            public void onFailure(Throwable t){
                //处理异常t
            }
        })
    }
    

7.3 Netty的异步回调模式

Netty 和 Guava 一样，实现了自己的异步回调体系：Netty继承和扩展了JDK Future异步回调API，定义了自己的Future 系列接口和类(注意：Netty的扩展Future也叫Future)，实现了异步任务的监控、异步执行结果的获取。

Netty 应用的 Handler 执行器中的业务代码都是异步执行的。

总体，Netty 对 Java Future 异步任务的扩展如下：

- 继承 Java 的 Future 接口
  得到了一个新的属于 Netty 自己的 Future 异步任务接口，对应 Guava 的 ListenableFuture 接口，但Netty的Future接口一般不会直接使用，而是会使用子接口，如ChannleFuture接口表示通道IO的异步任务
- 引入了新接口 GenericFutureListener
  用于表示异步执行完成的监听器，对应Guava的FutureCallback

示例代码如下：

    ChannelFuture future = bootstrap.connect(new InetSocketAddress(ip,port));
    future.addListener(new ChannelFutureListener(){
        @Override
        public void opertionComplete(ChannelFuture channelFuture) throws Exception{
            if(channelFuture.isSuccess()){
                //成功逻辑
            }else{
                //失败逻辑
                channelFuture.cause().printStackTrce();
            }
        }
    })

8. Netty原理与基础

Netty 是为了快速开发可维护的高性能、高可拓展、网络服务器和客户端程序而提供的异步事件驱动基础框架和工具。

8.1 一个简单的netty demo

1. 导入maven依赖
       <dependency>
       	<groupId>io.netty</groupId>
       	<artifactId>netty-all</artifactId>
       	<version>4.1.6.Final</version>
       </dependency>
2. 创建一个服务器端类
       public class NettyDiscardServer{
       	private final int serverPort;
           ServerBootstrap b = new ServerBootstap();
           public NettyDiscardServer(int port){
               this.serverPort = port;
           }
           public void runServer(){
               //创建反应器线程组
               EventLoopGroup bossLoopGroup = new NioEventLoopGroup(1);//负责IO事件监听
               EventLoopGroup workerLoopGroup = new NioEventLoopGroup();//负责IO事件的处理
               try{
                   //1.设置反应器线程组
                   b.group(bossLoopGroup, workerLoopGroup);
                   //2.设置nio类型的通道
                   b.channel(NioServerSocketChannel.class);
                   //3.设置监听端口
                   b.localAddress(this.serverPort);
                   //4.设置通道的参数
                   // 启用TCP心跳机制，只有在连接空闲的时候才会起作用
                   b.option(ChannelOption.SO_KEEPALIVE,true);
                   // 设置缓冲区的分配方式为池化分配器(Netty4支持)
                   b.option(ChannelOption.ALLOCATOR,PooledByteBufAllocator.DEFAULT);
                   //5.装配子通道流水线
                   b.childHandler(new ChannelInitializer<SocketChannel>(){
                       //有连接到达时会创建一个通道
                       protected void initChannel(SocketChannel ch) throws Exception{
                           //流水线管理子通道中Handler处理器
                           //向子通道流水线添加一个handler处理器
                           ch.pipeline().addList(new NettyDiscardHandler());
                       }
                   });
                   //6.开始绑定服务器
                   // 通过调用 sync 同步方法阻塞知道绑定成功
                   ChannelFuture channelFuture = b.bind().sync();
                   Logger.info("服务器启动成功，监听端口:%s",channelFuture.channel().localAddress());
                   //7.等待通道关闭的异步任务结束
                   //服务监听通道会一直等待通道关闭的异步任务结束
                   ChannelFuture closeFuture = channelFuture.channel().closeFuture();
                   closeFuture.sync();
               }catch(Exception e){
                   e.printStackTrace();
               }finally{
                   //8.关闭EventLoopGroup
                   //释放掉所有资源包括创建的线程
                   workLoopGroup.shutdownGracefully();
                   bossLoopGroup.shutdownGracefully();
               }
           }
           public static void main(String[] args) throws InterruptedException{
               int port = NettyDemoConfig.SOCKET_SERVER_PORT;
               new NettyDiscardServer(port).runServer();
           }
       }
   Netty是基于反应器模式实现的。之前说到反应器的作用是进行一个IO事件的selelct查询和dispatch分发。
   Netty中反应器组件有多种，一般来说，对应于多线程的Java Nio通信的应用场景，是NioEventLoopGroup;
   ServerBootstrap 是 Netty 的服务启动类，它的职责是一个组装和集成器，将不同的Netty组件组装在一起。另外，ServerBootstrap能够按照应用场景的需要，为组件设置好对应的参数，最后实现 Netty 服务器的监听和启动。
3. 定义业务处理器
       public class NettyDiscardHandler extends ChannelInboundHandlerAdapter{
           @Override
           public void channelRead(ChannelHandlerContext ctx,Object msg) throws Exception{
               ByteBuf in = (ByteBuf) msg;
               try{
                   Logger.info("收到消息");
                   while(in.isReadable()){
                       System.out.println((char) in.readByte());
                   }
               }finally{
                   //在最后一个处理器中需要释放缓冲区，缓冲区有多种释放方式，在后文中有介绍
                   ReferenceContUtil.relase(msg);
               }
           }
       }
       
   这里引入一个新概念：入站和出站。简单来说入站指输入，出站指的是输出。
   		Netty 的 Handler 处理器需要处理多种IO事件，Netty提供了一些基础的方法，这些方法都已经提前封装好，后面直接继承或者实现即可。比如处理入站的IO事件的方法，对应的接口为ChannelInboundHandler入站处理接口，而ChannelInboundHandlerAdapter是Netty提供的入站处理的默认实现。

8.2 Netty的Reactor反应器模式

8.2.1 Netty中的Channel

Netty 中 不直接使用 Java NIO 的 Channel 通道组件，对 Channel 进行了自己的封装。在 Netty 中，有一系列的通道组件，支持多种协议

对于不同协议，Netty 中常见的通道类型如下：

- NioSocketChannel - 异步非阻塞 TCP Socket 传输通道
- NioServerSocketChannel - 异步非阻塞 TCP Socket 服务器端监听通道
- NioDatagramChannel - 异步非阻塞的UDP传输通道
- NioSctpChannel - 异步非阻塞Sctp传输通道
- NioSctpServerChannel - 异步非阻塞Sctp服务器端监听端口
- OioSocketChannel - 同步阻塞 TCP Socket 传输通道
- OioServerSocketChannel - 同步阻塞 TCP Socket 服务器端监听通道
- OioDatagramChannel - 同步阻塞的UDP传输通道
- OioSctpChannel - 同步阻塞 Sctp传输通道
- OioSctpServerChannel - 同步阻塞Sctp服务器端监听端口

Channel的主要方法如下

- ChannelFuture connect(SocketAddress address)
  用于客户端 传输通道 连接远程服务器。该方法调用后会立刻返回，返回值为负责连接操作的异步任务。
- ChannelFuture bind(SocketAddress address)
  用于服务器的新连接监听和接收通道 绑定监听端口，开始监听新的客户端连接。
- ChannelFuture close()
  用于关闭通道连接。如果需要在正式关闭通道后执行一些操作。可以为异步任务设置回调方法或者直接调sync()方法阻塞当前线程一直到通道关闭的异步任务完毕。
- Channel read()
  读取通道数据，并启动入站处理。具体就是从内部的Java NIO通道读取数据，然后启用内部的流水线。此方法返回通道自身用于链式调用
- ChannelFuture write(Object o)
  启动出站处理。把处理后的最终数据写到Java NIO通道。此方法的返回值为出站处理的异步处理任务
- Channel flush()
  可以将缓冲区的数据立刻写出到对端，write操作大部分情况下仅仅是写入到操作系统的缓冲区，操作系统会将根据缓冲区的情况，决定什么时候把数据写到对端。

8.2.2 Netty中的Reactor反应器

Netty中的反应器有多个实现类，与Channel通道类型关联。比如NioSocketChannl通道，Netty的反应器类为NioEventLoop。

一个NioEnventLoop反应器拥有一个线程，其内部有一个Nio选择器成员执行事件的查询，然后进行对应事件的分发，而分发的目标就是Netty自己的Handler处理器

8.2.3 Netty中的Handler处理器

Netty的Handler处理器分为两大类，第一类是ChannelInboundHandler 通道入站处理器；第二类是ChannelOutboundHandler通道出站处理器。两者都继承了ChannelHandler处理器接口。

其实整个IO处理操作包括：从通道读取数据包(入站)、数据包解码(入站)、业务处理、目标数据编码(出站)、把数据包写到通道并发送到对端(出站)。只是除了业务处理需要用户程序负责，别的Netty底层已经完成。

8.2.4 Netty的流水线

先梳理一下Netty反应器模式中，各个组件之间的关系：

1. 反应器(或者SubReactor子反应器)和通道之间是一对多的关系：一个反应器可以查询多个通道的IO事件
2. 通道和Handler处理器实例之间，是多对多的关系：一个通道的IO事件可以被多个Handler处理，一个Handler处理器实例也能绑定多个通道

通道和Handler处理器的绑定关系，Netty是如何组织的？

Netty设计了一个特殊的组件，叫作ChannelPipeline（通道流水线），它是一个双向链表，它将绑定到一个通道的多个Handler处理器实例串在一起，形成一条流水线，这里用到了责任链设计模式。

IO事件在流水线上的执行顺序是怎么样的？

入站处理器Handler的执行次序，是从前到后(即和添加顺序相同)；出站处理器Handler的执行次序，是从后到前(与添加顺序相反)。

入站IO操作只能从Inbound入站处理器类型的Handler经过；出站IO操作只会从Outbound出站处理器类型的Handler经过

流水线上的处理器能否在运行期间动态变化？

可以的，Netty流水线支持处理器的热插拔。只需调用 ChannelHandlerContext 中对应方法：

addFirst(String name, ChannelHandler handler);//在头部添加一个业务处理器

addLast(String name,ChannelHandler handler);//在尾部添加一个业务处理器

addBefore/addAfter(String baseName, String name, ChannelHandler handler);//在baseName处理器前面/后面加一个业务处理器

remove(ChannelHandler channelHandler);//删除一个业务处理器实例

8.2.5 ByteBuf缓冲区

Netty 提供了 ByteBuf 来替换Java NIO 的ByteBuffer缓冲区，以操作内存缓冲区。

其和ByteBuffer存在以下区别：

- Pooling（因为缓冲区被频繁创建释放，Netty4开始支持池化，这点减少了内存复制和 GC，提升了效率）
- 复合缓冲区类型，支持零复制
- 不需要调用flip()方法去换换读/写模式
- 扩展性好，例如StringBuffer
- 可以自定义缓冲区类型
- 读取和写入索引分开
- 方法的链式调用
- 可以进行引用计数，方便重复使用

ByteBuf的逻辑结构

ByteBuf是一个字节容器，内部是一个字节数组。从逻辑上分，可以分为四个部分：

- 废弃 - 已用字节，表示使用完的废弃的无效字节
- 可读 - 从ByteBuf中读取的数据都来自这一部分
- 可写 - 写入到ByteBuf的数据都会写到这一部分中
- 可扩容 - 表示ByteBuf最多还能扩容多少

为了让读写之间没有冲突，ByteBuf内部维护了三个属性

- readerIndex(读指针)：当前读取的位置，每次读取自动加1，直到和写指针相等，表示读完
- writerIndex(写指针)：没写一个字节自动加1，如果写入的时候容量不足可以扩容
- maxCapacity(最大容量)：容量最大能扩展到的值，超过会报错

ByteBuf的内存分配

- ByteBufAllocator.DEFAULT.buffer(9,100); 分配初始容量为9，最大容量为100的缓冲区
- ByteBufAllocator.DEFAULT.buffer();分配初始容量为256，最大容量为Integer.MAX_VALUE的缓冲区
- UnpooledByteBufAllocator.DEFAULT.heepBuffer();
  非池化分配器，分配基于堆结构的堆缓冲区，特点是创建和销毁缓冲区的速度快
- PooledByteBufAlloctor.DEFAULT.directBuffer();
  池化分配器，分配基于操作系统的直接缓冲区，直接缓冲区的特点是写入传输通道比堆缓冲区快，但创建和销毁因为需要调用操作系统方法，成本高，因此尽量在池化中使用，避免频繁创建和销毁缓冲区

ByteBuf使用示例

    public class WriteReadTest{
        @Test
        public void testWriteRead(){
            //分配初始容量为9，最大容量为100的一个缓冲区
            ByteBuf buffer = ByteBufAllocator.DEFAULT.buffer(9,100);
            //写入缓冲区  如果写入其他类型数据可以调用对应writeTYPE方法
            buffer.writeBytes(new byte[]{1,2,3,4});
            //读字节，不改变指针
            getByteBuf(buffer);
            //取字节，改变指针
            readByteBuf(buffer);
        }
        
        //读字节
        private void getByteBuf(ByteBuf buffer){
            for(int i=0;i<buffer.readableBytes();i++){
                Logger.info(buffer.getByte(i));
            }
        }
        
        //取字节
        private void readByteBuf(ByteBuf buffer){
            while(buffer.isReadable()){
                Logger.info(buffer.readByte());
            }
        }
    }
    

ByteBuf在Netty中的释放

入站的释放方式：

1. TailHandler自动释放
   Netty默认会再ChannlePipline通道流水线的最后添加一个TailHandler末尾处理器，在默认情况下，处理器会将最初的ByteBuf数据包一路往下传，那么TailHandler就能释放掉缓冲区了
   使用方法为，入站处理器继承自ChannelInboundHandlerAdapter适配器，那么可以调用以下两种方式：
   - 手动释放 - byteBuf.release();
   - 将msg向后传- super.channelRead(ctx,msg);
2. SimpleChannelInboundHandler自动释放
   如果业务处理器需要截断流水线的处理流程，不将ByteBuf数据包送入后边的TailHandler，在这种场景下，Handler业务处理器有两种选择：
   - 手动释放ByteBuf实例
   - 继承SimpleChannelInboundHandler，利用它的自动释放功能
     重写channelRead0方法，源码中，执行完channelRead0方法后，如果ByteBuf的计数器为0，将彻底被释放

出站的释放方式：

HeadHandler自动释放，因为出站是执行器从后往前执行，所以在TailHandler相反，出站的缓冲区释放操作在HeadHandler中执行。

ByteBuf浅层复制的高级使用方式

浅层复制可以很大程度避免内存赋值。这一点对于大规模通信来说是非常重要的。

ByteBuf的浅层复制分为两种：

- 切片(slice)浅层复制
  ByteBuf的slice方法可以获取到一个ByteBuf切片。
- 整体(duplicate)浅层复制

8.3 Bootstrap 启动类

Netty为了快速将通道、反应器、处理器快速组装起来，提供了启动类Bootstrap。

在Netty中，有两种启动类，分别用在 服务器 和 客户端

- Bootstrap - client专用
- ServerBootstrap - server专用

这两种启动器只是使用的地方不同，它们大致的配置和使用方法都是相同的。

8.3.1 父子通道

在Netty中，将有接收关系的NioServerSocketChannel 和 NioSocketChannel叫做父子通道。其中，父通道负责服务器监听和接收；对应于每一个接收到的子通道是传输类通道。

8.3.2 EventLoopGroup线程组

在Netty中，一个 EventLoop 相当于一个子反应器。一个NioEvnetLoop子反应器拥有一个线程，为了组织这些反应器有了EventLoopGraoup线程组

构造方法可以定义线程组数量，如果没有定义，默认会使用系统内核数的两倍

8.3.3 ServerBootstrap 的启动流程

以下为服务器端启动器的流程：

正式使用前，需要先创建一个服务器端的启动器实例

    ServerBootstrap b = new ServerBootstrap();
    

1. 创建反应器线程池，并赋值给 ServerBootstrap 启动器实例
       // boss线程组
       EventLoopGroup bossLoopGroup = new NioEventLoopGroup(1);
       // worker线程组
       EventLoopGroup workerLoopGroup = new NioEventLoopGroup();
       //设置 反应器线程组
       b.group(bossLoopGroup, workerLoopGroup);
       
   boss线程组负责处理连接监听IO事件，worker线程组负责IO事件和Handler业务处理，group方法也可以只有一个参数，但在父子通道模型下只是用一个线程组会导致监听受业务处理阻塞。
2. 设置通道的IO类型
   Netty支持OIO、NIO和不同的传输协议（这里是设置父通道类型）
       b.channel(NioServerSocketChannel.class);
       
3. 设置监听端口
       b.loacalAddress(new InetSocketAddress(port));
       
4. 设置传输通道的配置选项
       b.option(ChannelOption.SO_KEEPLIVE, true);
       b.option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
       
   这里是给父通道设置一些选项，如果要给子通道设置可以使用childOption()方法
   下面介绍一些常见的选项
   - SO_RCVBUF\SO_SNDBUF
     这两个参数用来设置内核发送缓冲区/接收缓冲区大小。之所以划分为两个缓冲区是为了支持全双工的工作模式。
   - TCP_NODELAY
     改参数表示是否要立刻发送数据（默认为True），设置为True可以最小化报文传输的时延，设置为False可以最小化发送报文的数量，减少网络交互次数
   - SO_KEEPALIVE
     是否启用TCP心跳机制，默认为false，启用后默认的心跳间隔是2小时
   - SO_LINGER
     表示TCP socket的关闭时延，默认为-1，表示socket.close()方法会立刻返回，但操作系统会异步将发送缓冲区的数据发送到对端。0表示方法立刻返回，并放弃发送发送缓冲区数据，对端将收到复位错误。>0表示close方法将阻塞，直到延迟时间到来，如果没发送完，对端将收到复位错误
   - SO_BACKLOG
     表示TCP 服务端接收连接的队列长度，如果队列已满，客户端的连接将被拒绝。默认值，windows为200，其他是128
   - SO_BROADCAST
     这是TCP参数，表示设置为广播模式
5. 配置子通道的Pipeline流水线
   使用childHandler方法，传递一个通道初始化方法。在父通道成功接收到一个连接，并创建一个子通道的时候会调用初始化方法。
       b.childHandler(new ChannelInitializer<SocketChannle>(){//泛型类型是之前启动类设置的父通道类型对应的子通道类型
       	//有连接到达时会创建一个通道的子通道，并初始化
       	protected void initChannel(SocketChannel ch) throws Exception{
               ch.pipeline().addList(new NettyDiscardHandler());
           }
       })
       
   一般只需要配置子通道的初始化方法，父通道因为内部处理逻辑是固定的：接收新连接，创建子通道，然后初始化子通道，但如果需要完成特殊业务处理，可以使用.handler(ChannelInitialize init);为父通道设置初始化方法。
6. 绑定之前设置的服务器监听端口
       ChannelFuture channelFuture = b.bind().sync();
       Logger.info("服务器启动成功，监听端口：%s", channelFuture.channel().localAddress());
       
   这是是阻塞的方式执行异步任务，在Netty中，所有的IO操作都是异步执行的，这就意味着任何一个IO操作会立刻返回，在返回的时候，异步任务还没有真正执行。
   Netty中的IO操作，都会返回异步任务实例(如ChannelFuture实例)，通过自我阻塞一直到ChannelFuture异步任务执行完成。
7. 自我阻塞，直到通道关闭
       ChannelFuture closeFuture = channelFuture.channel().closeFuture();
       clseFuture.sync();
       
   如果要阻塞当前线程直到通道关闭，可以使用通道的closeFuture()方法，以获取通道关闭的异步任务。当通道被关闭时，closeFuture实例的sync()方法会返回。
8. 关闭EventLoopGroup
       workerLoopGroup.shutdownGracefully();
       bossLoopGroup.shutdownGracefully();
       
   此过程，关闭反应器线程组、子反应器线程、Selector选择器、内部的轮询线程、负责查询的所有子通道、释放掉自层资源

9. Netty版本回显服务器

功能：从服务器端读取客户端输入的数据，然后将数据直接回显到Console控制台

9.1 服务器端的ServerBootstrap

    public class NettyEchoServer{
    	ServerBootstrap b = new ServerBootstrap();
        private final int serverPort;
    
        public NettyEchoServer(int serverPort) {
            this.serverPort = serverPort;
        }
    
        public void runServer() {
            EventLoopGroup bossLoopGroup = new NioEventLoopGroup(1);
            EventLoopGroup workerLoopGroup = new NioEventLoopGroup();
            try {
                b.group(bossLoopGroup, workerLoopGroup);
                b.channel(NioServerSocketChannel.class);
                b.localAddress(this.serverPort);
                b.option(ChannelOption.SO_KEEPALIVE, true);
                b.option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
                b.childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(NettyEchoServerHandler.INSTANCE);
                    }
                });
                ChannelFuture channelFuture = b.bind().sync();
                log.info("服务器启动成功，监听口:{}", channelFuture.channel().localAddress());
                ChannelFuture closeFuture = channelFuture.channel().closeFuture();
                closeFuture.sync();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                workerLoopGroup.shutdownGracefully();
                bossLoopGroup.shutdownGracefully();
            }
        }
        
        public static void main(String[] args) throws InterruptedException{
            new NettyEchoServer(10001).runServer();
        }
    }

9.2 NettyEchoServerHandler处理器

    @Slf4j
    @ChannelHandler.Sharable
    public class NettyEchoServerHandler extends ChannelInboundHandlerAdapter {
        public static final NettyEchoServerHandler INSTANCE = new NettyEchoServerHandler();
    
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            ByteBuf in = (ByteBuf) msg;
            //Netty4.1开始，ByteBuf的默认类型是直接内存缓冲区
            log.info("缓冲区类型:{}", in.hasArray() ? "堆缓冲区" : "直接缓冲区");
            int len = in.readableBytes();
            byte[] arr = new byte[len];
            //使用get方式数据在msg中还保留
            in.getBytes(0, arr);
            log.info("服务端接收到：{}", new String(arr, StandardCharsets.UTF_8));
            log.info("写回前，引用计数：{}", ((ByteBuf) msg).refCnt());
            //写回数据，异步任务
            ChannelFuture f = ctx.writeAndFlush(msg);
            f.addListener((ChannelFuture futureListener) -> log.info("写回后，引用计数:{}", ((ByteBuf) msg).refCnt()));
        }
    }

这里使用了注解@ChannelHandler.Sharable表示这个Handler实例可以被多个通道安全地共享，也就是说多个通道的流水线可以加入同一个Handler业务处理实例。而这种操作，Netty默认是不允许的。

但Handler中经常只有业务操作，不涉及线程安全问题，这时候如果服务器要处理很多通道，就没必要创建这么多处理器了

9.3 客户端Bootstrap

    @Slf4j
    public class NettyEchoClient {
    	private int serverport;
        private String serverIp;
        Bootstrap b = new Bootstrap();
    
        public NettyEchoClient(String ip, int serverport) {
            this.serverIp = ip;
            this.serverport = serverport;
        }
    
        public void runClient() {
            EventLoopGroup workerLoopGroup = new NioEventLoopGroup();
            try {
                b.group(workerLoopGroup);
                b.channel(NioSocketChannel.class);
                b.remoteAddress(serverIp, serverport);
                b.option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
                b.handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline().addLast(NettyEchoClientHandler.INSTANCE);
                    }
                });
                ChannelFuture f = b.connect();
                f.addListener((ChannelFuture futureListener) -> {
                    if (futureListener.isSuccess()) {
                        log.info("EchoClient客户端连接成功");
                    } else {
                        log.info("EchoClient客户端连接失败");
                    }
                });
                //阻塞，直到连接成功
                f.sync();
                Channel channel = f.channel();
                Scanner scanner = new Scanner(System.in);
                System.out.println("请输入内容");
                while (scanner.hasNext()) {
                    String next = scanner.next();
                    byte[] bytes = (LocalDateTime.now() + ">>" + next).getBytes(StandardCharsets.UTF_8);
                    ByteBuf buf = channel.alloc().buffer();
                    buf.writeBytes(bytes);
                    channel.writeAndFlush(buf);
                    System.out.println("请输入内容");
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                workerLoopGroup.shutdownGracefully();
            }
        }
    }
    

9.4 NettyEchoClientHandler 处理器

    @Slf4j
    @ChannelHandler.Sharable
    public class NettyEchoClientHandler extends ChannelInboundHandlerAdapter {
        public static final NettyEchoClientHandler INSTANCE = new NettyEchoClientHandler();
    
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            ByteBuf byteBuf = (ByteBuf) msg;
            int len = byteBuf.readableBytes();
            byte[] arr = new byte[len];
            byteBuf.getBytes(0, arr);
            log.info("client receive:{}", new String(arr, StandardCharsets.UTF_8));
            byteBuf.release();
            //super.channelRead(ctx.msg);
        }
    }
    

上面的代码中都是手动进行ByteBuf二进制数据的解码，和Java POJO 的编码，下面介绍如果使用 Decoder 和 Encoder组件。

10. 编码及解码

在入站处理的时候，需要将ByteBuf二进制类，解码成Java POJO对象。这个解码过程，可以通过Netty的Decoder解码器去完成。

在出站处理的时候，需要将业务处理结果（POJO对象）编码为ByteBuf二进制数据，然后通过Java通道发送到对端，可以通过Netty的Encoder解码器去完成。

10.1 Decode 原理与实践

1. 它会处理入站数据
2. 将上一站Inbound处理器传来的数据进行解码或者格式转换

Netty内置了这个解码器，叫做ByteToMessageDecoder，位于 Netty 的 io.netty.handler.codec 包中

所有Netty中的解码器，都是Inbound入站处理器类型，都直接或间接实现了 ChannelInboundHandler 接口

ByteToMessageDecoder解码器

ByteToMessageDecoder是一个抽象类解码器，继承自 ChannelInboundHandlerAdapter适配器，具体的解码流程在子类中实现，如果要实现一个自己的解码器，流程如下：

1. 继承ByteToMessageDecoder抽象类
2. 实现基类的 decode 方法，将 ByteBuf 二进制数据解码成 Java POJO 对象的逻辑写入此方法
3. 在decode方法中，还需要将解码后的Java POPO对象放入 decode 的List<Object>实参中。
4. 最后父类会将List<Object>中的结果，一个个分开传递给下一站Inbound入站处理器（不需要实现）

10.1.1 通过嵌入式通道测试自定义解码器

在 Netty 的实际开发中，大量的工作是设计和开发 入站处理器，而不是出站处理器。

开发完成后，需要投入单元测试，大致流程为：将Handler业务处理器加入通道的Pipeline流水线，先后启动Netty服务器、客户端，然后互发消息，测试业务处理器的结果。

这样是非常繁琐的，所以Netty提供了专门的嵌入式通道——EmbeddedChannel，不需要进行实际的传输

其主要方法

- writeInbound - 向通道写入入站数据
- readInbound - 从通道中读取入站数据，是经过入站处理器处理后的入站数据
- writeOutbound - 向通道写入出站数据
- readOutbound - 从通道中读取出站数据，是经过出站处理器处理后的出站数据

下面通过EmbededChannel自定义整数解码器：

首先需要定义一个解码器

    public class Byte2IntegerDecoder extends ByteToMessageDecoder{
        @Override
        public void decode(ChannelHandlerContext ctx,ByteBuf in,List<Object> out){
            while(in.readableBytes() >= 4){
                int i = in.readInt();
                log.info("解码出一个整数{}",i);
                out.add(i);
            }
        }
    }
    

定义一个业务处理器

    public class IntegerProcessHandler extends ChannelInboundHandlerAdapter{
    	@Override
        public void channelRead(ChannelHandlerContext ctx,Object msg) throws Exception{
            Integer integer = (Integer) msg;
            log.info("打印出一个整数:{}",integer);
        }
    }
    

使用EmbeddedChannnel嵌入式通道测试入站

    public class Byte2IntegerDecoderTester{
        ChannelInitializer i = new ChannelInitializer<EmbeddedChannel>(){
            protected void initChannel(EmbeddedChannel ch){
                ch.pipeline().addList(new Byte2IntegerDecoder());//先解码
                ch.pipeline().addList(new IntegerProcessHandler());//后处理
            }
        };
        EmbeddedChannel channel = new EmbeddedChannel(i);
        for(int j = 0; j<100;j++){
            ByteBuf buf = Unpooled.buffer();
            buf.writeInt(j);
            channel.writeInbound(buf);
        }
        try{
            Thread.sleep(Integer.MAX_VALUE);
        }catch(Exception e){}
    }
    

10.1.2 粘包和拆包

Netty发送和读取数据的场所是ByteBuf缓冲区。每一次发送就是向通道写入一个ByteBuf；每一次读取就是通过Handler业务处理器的入站方法，从通道读到一个ByteBuf。

最为理想的情况是：发送端每发送一个ByteBuf缓冲区，接收端就能接收到一个ByteBuf，并且发送端和接收端的ByteBuf内容一模一样。

我们改造之前的Netty客户端，将手动输入发送数据的方式修改为循环发送1000次数据

    f.sync();
    Channel channel = f.channel();
    Stirng content = "需要循环发送的数据";
    byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
    for(int i = 0;i<1000;i++){
        ByteBuf buffer = channel.alloc().buffer();
        buffer.writeBytes(bytes);
        channel.writeAndFlush(buffer);
    }
    

查看程序运行结果，发送存在三种类型的输出：

- 读到一个完整的客户端输入 ByteBuf（全包）
- 读到多个客户端的 ByteBuf 输入，但是粘在一起（粘包）
- 读到部分 ByteBuf 内容，并且有乱码（半包）

半包和粘包统称为半包现象

半包现象出现的原理

写入数据的大致流程为：在应用进程缓冲区，应用程序以ByteBuf为单位发送数据，复制到内核缓冲区，底层对数据包进行二次拼装，拼装成传输层TCP层的协议报文，再发送。

读取数据的大致流程为：当IO可读时，底层将二进制数据从网卡读取，并按照网络协议规范进行拆包后读到内核缓冲区，Netty读取ByteBuf时，二进制数据从内核缓冲区复制到进程缓冲区。

在Netty程序从内核缓冲区复制到Netty进程缓冲区ByteBuf时，问题来了：

- 每次读取底层缓冲的数据容量是有限制的，当TCP底层缓冲的数据包比较大时，会将一个底层包分成多次ByteBuf进行复制，进而造成进程缓冲区读到的是半包
- tcp为提高传输效率，发送方往往要手机到足够多的数据后才发送一包数据，这可能导致粘包
- 接收方如果应用进程不及时接收数据，内核缓冲区中后一包数据接在前一包数据后，而用户进程依然根据预先设定的缓冲区大小从内核缓冲区读取数据，这可能导致粘包

而分包（拆包）就是解决半包现象的手段

10.1.3 分包方案

使用上面的解码器会面临一个问题：需要对ByteBuf的长度进行判断，这个操作可用交给Netty的RepleyingDecoder类来完成

ReplayingDecoder 类是 ByteToMessageDecoder 的子类，其作用是：

- 在读取ByteBuf缓冲区数据前，检查缓冲区是否有足够字节
- 若ByteBuf中有足够的字节，则会正常读取；反之，则停止解码
- 解决长数据分包，导致接收端的包和发送端发送的包不一致问题

在Java OIO传输中，不会出现这样的问题，因为它的策略是：不读到完整的信息，就一直阻塞程序，不向后执行，但由于Java NIO 的非阻塞性，包在传输过程中进行多次的拆分和组装，无法保证一次性读到完整的数据。

除了使用RepleyingDecoder定义自己的进程缓冲区分包器 还可以使用Netty内置的解码器

Netty针对不同数据类型，提供了不同的解决方案，以下是Netty内置的一些解码器：

1. 固定长度数据包解码器——FixedLengthFrameDecoder
   适用于，缓冲区每个接收到的数据包长度是固定的。该解码器加入到流水线后，会将入站ByteBuf缓冲区拆分成一个个固定长度的数据包，然后发往下一个入站处理器
2. 行分割数据包解码器——LineBasedFrameDecoder
   适用于，每个ByteBuf数据包，适用换行符作为边界分隔符
       ch.pipeline().addLast(new FixedLengthFrameDecoder(1024));//参数是最大的行长度，如果超过抛异常
       
3. 自定义分隔符数据包解码器——DelimiterBasedFrameDecoder
   分隔符不受限于换行符
       ch.pipeline().addLast(new DelimiterBasedFrameDecoder(1024,true,Unpooled.copiedBuffer("\t".getBytes("UTF-8"))));//第二个参数表示解码后数据包是否去掉分隔符
       
4. 自定义长度数据包解码器——LengthFieldBasedFrameDecoder
   在ByteBuf数据包中，加了一个长度字段，保存了原始数据包的长度。解码的时候，会按照这个长度进行原始包的提取。
   普通的基于Header-Content协议的内容传输，尽量使用LengthFieldBasedFrameDecoder解码
       public class NettyOpenBoxDecoder{
           
           @Test
           public void testLengthFieldBasedFrameDecoder1(){
               try{
                   /**参数按顺序分别是
                       发送的数据包最大长度
                       长度字段偏移量：长度字段位于数据包的字节数组的下表位置
                       长度字段自己占用的字节数：长度字段占用的字节数
                       长度字段的偏移量矫正：在传输协议比较复杂的情况下，例如包含了长度字段、协议版本号、魔数等等，那么，解码就需要进行长度矫正：矫正值的计算公式为：内容字段偏移量-长度字段偏移量-长度字段的字节数
                       丢弃的起始字节数：一些起辅助作用的字段，最终结果不需要，比如前面的长度字段
                       **/
                   final LengthFieldBasedFrameDecoder spliter = new LengthFieldBasedFrameDecoder(1024,0,4,0,4);
                   ChannelInitializer i = new ChannelInitializer<EmbeddedChannel>(){
                       protected void initChannel(EmbeddedChannel ch) {
                           ch.pipeline().addLast(spliter);
                           ch.pipeline().addLast(new StringDecoder(Charset.forName("UTF-8")));
                           ch.pipeline().addLast(new StringHandler());
                       }
                   };
                   EmbeddedChannel channel = new EmbeddedChannel(i);
                   for (int j = 1;j<= 100;j++){
                       ByteBuf buf = Unpooled.buffer();
                       String s = j+"次发送";
                       byte[] bytes = s.getBytes("UTF-8");
                       buf.writeInt(bytes.length);
                       buf.writeBytes(bytes);
                       channel.writeInbound(buf);
                   }
                   Thread.sleep(Integer.MAX_VALUE);
               }catch(InterruptedException e){
                   e.printStackTrace();
               }catch(UnsupportedEncodingException e){
                   e.printStackTrace();
               }
           }
       }
       

10.2 Encoder 原理与实践

编码器首先是一个出站处理器，负责处理出站数据；同时也会处理上一站出站处理器处理结果，将java Pojo对象转为二进制字节流

MessageToByteEncoder编码器

与解码器相反，继承该父类后需要实现encode方法，使用out.writeInt(msg);将javaPOJO类型写入到ByteBuf二进制流中

10.3 编码器与解码器的结合

在实际开发中，由于数据的入站和出站关系密切，既要定义编码器，又要定义解码器，需要两次加入通道的流水线。Netty提供了具有相互配套逻辑的编码器和解码器——Codec类型

ByteToMessageCodec编解码器

提供了两个抽象方法：encode,decode需要同时实现，也只需要加入一次流水线

CombinedChannelDuplexHandler组合器

为了既能单独使用编码器、解码器，又能结合使用，netty提供解码器来栓绑编码器、解码器，同时和ByteToMessageCodec比，他们又能单独使用

    public class IntegerDuplexHandler extends CombinedChannelDuplexHandler<Byte2IntegerDecoder,Integer2ByteEncoder>{
        public IntegerDuplexHandler(){
            super(new Byte2IntegerDecoder(),new Integer2ByteEncoder());
        }
    }
    

11. JSON和ProtoBuf序列化

11.1 JSON传输

目前java主流的json开源框架：阿里的FastJson、谷歌的Gson、开源社区的Jackson。

Jackson

优点：依赖的jar包少、简单、性能不错、社区活跃

缺点：对于复杂POJO类型、负责集合Map、List转换的结果不是标准的JSON格式，甚至会出现一些问题

Gson

优点：开源完成复杂类型的POJO和JSON字符串的相互转换，转换能力强

缺点：性能会差一些

FastJson

优点：高性能

缺点：在复杂类型的POJO转化JSON时，可能会出现一些引用类型而导致出错

目前主流的策略是POJO序列化为JSON时使用Gson，在JSON字符串反序列化成POJO时，使用FastJson

JSON通用工具类

    import com.alibaba.fastjson.JSONObject;
    import com.google.gson.GsonBuilder;
    
    public class JsonUtil{
        static GsonBuilder gb = new GsonBuilder();
        static{
            gb.disableHtmlEscaping();
        }
        //序列化
        public static String pojoToJson(Object obj){
            String json = gb.create().toJson(obj);
            return json;
        }
        //反序列化
        public static <T>T jsonToPojo(String json,Class<T> tClass){
            T t = JSONObject.parseObject(json, tClass);
            return t;
        }
    }

JSON编码器与解码器

以常用的 Head-Content 协议来介绍一下JSON传输

解码的流程如下：

1. 先使用LengthFieldBasedFrameDecoder(Netty内置自定义长度数据包解码器)解码出二进制数据的内容部分
2. 使用StringDecoder字符串解码器(Netty内置的解码器)将二进制内容解码成JSON字符串
3. 使用JsonMsgDecoder解码器(自定义解码器)，将Json字符串解码成POJO对象

编码流程如下：

1. 先使用StringEncoder编码器(内置)将JSON字符串编码成二进制字节数组
2. 使用LengthFieldPrepender编码器(内置)将二进制字节数组编码成Head-Content二进制数据包

LengthFieldPrepender编码器能在数据包的前面加上内容的二进制字节数组的长度，和LengthFieldBasedFrameDecoder常常配套使用

以下为使用json协议进行数据传输的netty demo：

服务端代码：

    @Slf4j
    public class JsonServer {
    
        private ServerBootstrap b = new ServerBootstrap();
    
        public void runServer() {
            EventLoopGroup bossLoopGroup = new NioEventLoopGroup(1);
            EventLoopGroup workerLoopGroup = new NioEventLoopGroup();
            try {
                b.group(bossLoopGroup, workerLoopGroup);
                b.localAddress(10011);
                b.channel(NioServerSocketChannel.class);
                b.option(ChannelOption.SO_KEEPALIVE, true);
                b.option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
                b.childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(1024, 0, 4, 0, 4));
                        ch.pipeline().addLast(new StringDecoder(CharsetUtil.UTF_8));
                        ch.pipeline().addLast(new JsonMsgDecoder());
                    }
                });
                ChannelFuture f = b.bind().sync();
                f.addListener((ChannelFuture futureListener) -> {
                    if (futureListener.isSuccess()) {
                        log.info("服务成功启动,监听的端口为{}",futureListener.channel().localAddress());
                    } else {
                        log.info("服务启动失败");
                    }
                });
                f.channel().closeFuture().sync();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                workerLoopGroup.shutdownGracefully();
                bossLoopGroup.shutdownGracefully();
            }
        }
    
        static class JsonMsgDecoder extends ChannelInboundHandlerAdapter {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                String json = (String) msg;
                JsonMsg jsonMsg = JsonMsg.toMsg(json);
                log.info("收到的POJO：{}", jsonMsg);
                super.channelRead(ctx, msg);
            }
        }
    }
    

客户端代码为：

    @Slf4j
    public class JsonSendClient {
    
        Bootstrap b = new Bootstrap();
    
        public void runClient() {
            EventLoopGroup workerLoopGroup = new NioEventLoopGroup();
            try {
                b.group(workerLoopGroup);
                b.channel(NioSocketChannel.class);
                b.remoteAddress("127.0.0.1", 10011);
                b.option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
                b.handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline().addLast(new LengthFieldPrepender(4));
                        ch.pipeline().addLast(new StringEncoder(CharsetUtil.UTF_8));
                    }
                });
                ChannelFuture channelFuture = b.connect();
                channelFuture.addListener((ChannelFuture futureListener) -> {
                    if (futureListener.isSuccess()) {
                        log.info("EchoClient客户端连接成功");
                    } else {
                        log.info("EchoClient客户端连接失败");
                    }
                });
                channelFuture.sync();
                Channel channel = channelFuture.channel();
                JsonMsg jsonMsg = JsonMsg.builder()
                        .id(123)
                        .content("发送的内容")
                        .build();
                channel.writeAndFlush(jsonMsg.convertToJson());
                log.info("发送json数据:{}", jsonMsg);
                ChannelFuture channelFuture1 = channel.closeFuture();
                channelFuture1.sync();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                workerLoopGroup.shutdownGracefully();
            }
        }
    }
    

11.2 Protobuf协议通信

11.2.1 原生Protobuf

Protobuf既独立于语言、又独立于平台。Protobuf序列化后是二进制格式，相对于文本格式的数据(json、xml)来说，要快的多。由于Protobuf性能优异，常用于分布式应用场景或异构场景的数据交换

Protobuf的编码过程为：使用预先定义的Message数据结构将实际的传输数据进行打包，然后编码成二进制的码流进行传输

Protobuf的解码过程为：将二进制码流解码成Protobuf自己定义的Message结构的POJO实例

.proto文件

Protobuf使用proto文件来预先定义消息格式，解码和编码都是根据文件进行的

    syntax = "proto3";
    package rmblc.cashInfo;
    
    option java_package = "com.chainter.rmblc.invest.bean.proto";//生成文件所在包路径
    option java_outer_classname = "CashInfo";//生成文件名
    
    //所有的消息会作为一个内部类，被打包到一个外部类中
    
    //头寸参数
    message SubCashInfoParam {
        string portfolioCode = 1;
    }
    
    //返回数据格式
    message RespData {
        RespCashInfoData data = 1;
    }
    
    //头寸数据
    message RespCashInfoData {
        PtPortfolioCash cash = 1;//头寸信息
    }
    
    message PtPortfolioCash{
        string id = 1;
    }
    

每个message相当于生成java中的一个类，一个字段相当于java中的一个类成员，数字表示该字段在序列化和反序列化的时候该字段的具体排序

如何生成POJO和Builder对象？

1. 通过控制台命令方式
   需要从https://github.com/protocolbuffers/protobuf/releases下载Protobuf的安装包，解压后配置系统环境变量的path变量，添加Protobuf的安装目录，然后运行以下命令生成：
       protoc.exe --java_out=./src/main/java/ ./Msg.proto
   表示要生成的原proto文件为：Msg.proto，生成到的路径为./src/main/java
2. Maven插件protobuf-maven-plugin生成
   在maven的pom中添加次plugin的配置项：
       <plugin>
            <groupId>org.xolstice.maven.plugins</groupId>
         <artifactId>protobuf-maven-plugin</artifactId>
            <version>0.5.1</version>
            <configuration>
                 <!--proto文件目录-->
                 <protoSourceRoot>${project.basedir}/protobuf</protoSourceRoot>
           	  <!--目标路径-->
                 <outputDirectory>${project.build.sourceDirectory}</outputDirectory>
                 <!--设置是否在生成Java文件之前清空outputDirectory的文件-->
                 <clearOutputDirectory>false</clearOutputDirectory>
                 <!--临时目录-->
                 <temporaryProtoFileDirectory>
                		${project.build.directory}/protoc-temp
                 </temporaryProtoFileDirectory>
                 <!--protoc 可执行文件路径-->
                 <protocExecutable>
                 		${project.basedir}/protobuf/protoc3.6.1.exe
                 </protocExecutable>
           </configuration>
            <executions>
                 <execution>
                        <goals>
                              <goal>compile</goal>
                              <goal>compile-custom</goal>
                        </goals>
                 </execution>
            </executions>
       </plugin>

如何使用生成的Pojo和Builder

1. 在maven的pom.xml文件
       <dependency>
           <groupId>com.google.protobuf</groupId>
           <artifactId>protobuf-java</artifactId>
           <version>${protobuf.version}</version>
       </dependency>
   这里的版本和.proto文件中配置的版本，以及编译器protoc.exe的版本必须是配套的
2. 使用Builder构造消息对象
       public class ProtobufDemo{
           public static MsgProtos.Msg buildMsg(){
               MsgProtos.Msg.Builder personBuilder = MsgProtos.Msg.newBuilder();
               personBuilder.setId(1000);
               persionBuilder.setContent("1111");
               MsgProtos.Msg message = personBuilder.build();
               return message;
           }
           ......
       }
       
3. 序列化&反序列化方式一
       @Test
       public void serAndDesr1() throws IOException{
           MsgProtos.Msg message = buildMsg();
           //将Protobuf对象序列化成二进制字节数组
           byte[] data = message.toByteArray();
           //可以用于网络传输，保存到内存或外存
           ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
           outputStream.write(data);
           data = outputStream.toByteArray();
           //二进制字节数组反序列化成Protobuf对象
           MsgProtos.Msg inMsg = MsgProtos.Msg.parseFrom(data);
           log.info("id=>{}",inMsg.getId());
           log.info("content=>{}",inMsg.getContent());
       }
       
4. 序列化&反序列化方式二
       @Test
       public void serAndDesr2() throws IOException {
           MsgProtos.Msg message = buildMsg();
           //序列化为二进制字节数组
           ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
           message.writeTo(outputStream);
           //二进制字节数组 反序列化 为protobuf对象
           ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
           MsgProtos.Msg inMsg = MsgProtos.Msg.parseFrom(inputStream);
       }
       
   在NIO场景下存在粘包/半包问题
5. 序列化&反序列化方式三
       @Test
       public void serAndDesr3() throws IOException {
           MsgProtos.Msg message = buildMsg();
           //序列化
           ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
           message.writeDelimitedTo(outputStream);
           //反序列化
           ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
           MsgProtos.Msg inMsg = MsgProtos.Msg.parseDelimitedFrom(inputStream);
       }
       
   在序列化前添加了字节数组的长度，类似于Head-Content协议，只不过长度的类型不是固定长度的Int类型，而是可变长varint32类型

11.2.2 Netty内置的PrtotoBuf用法

Netty内置的Protobuf专用的基础编码器/解码器为：ProtobufEncoder编码器和ProtobufDecoder解码器

ProtobufEncoder编码器

直接使用了message.toByteArray()将POJO消息转为二进制字节，数据放入Bytebuf数据包中，然后交给了下一站的编码器

ProtobufDecoder解码器

需要指定一个POJO消息的prototype原型POJO，根据原型实例找到对应的Parser解析器，将二进制的字节解析为Protobuf POJO消息对象

但仅仅使用以上这对编码器解码器会存在粘包/半包问题。Netty也提供了配套的Head-Content类型的Protobuf编码器和解码器，在二进制码流之前加上二进制字节数组的长度：

ProtobufVarint32LengthFieldPrepender长度编码器

在ProtobufEncoder生成的字节数组之前，前置一个varint32数字，表示序列化的二进制字节数

ProtobufVarint32FrameDecoder长度解码器

根据数据包中varint32中的长度值，解码一个足额的字节数组。然后建个字节数组交给下一站的解码器ProtobufDecoder

什么是varint32，为什么不用int这种固定类型的长度？

varint32是一种紧凑的表示数字的方法，它不是一种具体的数据类型。它根据值的大小自动进行长度的收缩，所以能更好地减少通信过程中的传输量

使用方式如下：

服务端：

    ch.pipeline().addLast(new ProtobufVarint32FrameDecoder());//根据长度从入站数据包解码出二进制Protobuf字节码
    ch.pipeline().addLast(new ProtobufDecoder(MsgProtos.Msg.getDefaultInstance()));//将字节码解码成Protobuf POJO对象
    ch.pipeline().addLast(new ProtobufBussinessDecoder());//处理Protobuf POJO对象
    
    static class ProtobufBusinessDecoder extends ChannelInboundHandlerAdapter{
        @Override
        public void channelRead(ChannelHandlerContext ctx,Object msg) throws Exception{
            MsgProtos.Msg protoMsg = (MsgProtos.Msg) msg;
            Logger.info("收到一个数据包"+protoMsg.getId());
        }
    }
    

客户端：

    ch.pipeline().addLast(new ProtobufVarint32LengthFieldPrepender());
    ch.pipeline().addLast(new ProtobufEncoder());
    //....
    ChannelFuture f = b.connect();
    f.sync();
    Channel channel = f.channel();
    for(int i = 0;i<1000;i++){
        MsgProtos.Msg user = build(i,i+"->"+content);
        channel.writeAndFlush(user);
    }
    channel.flush();
    
    public MsgProtos.Msg build(int id,String content){
        MsgProtos.Msg.Builder builder = MsgProtos.Msg.newBuilder();
        builder.setId(id);
        builder.setContent(content);
        ruturn builder.build();
    }
    

复杂的传输应用场景下(Head部分加上魔数字段 即对口令进行安全验证，或者需要对Data内容进行加密、解密等)，需要定制属于自己的Protobuf编码器和解码器，自定义ProtoBuf编解码器，解决半包问题，包括以下两个方面：

1. 继承netty提供的MessageToByteEncoder编码器，完成Head-Content协议的复杂数据包的编码，将Protobuf POJO编码成Head-Content协议的二进制ByteBuf数据包
2. 继承netty提供的ByteToMessageDecoder解码器，完成Head-Content协议的复杂数据包的解码，将二进制ByteBuf数据包最终解码出Protobuf POJO实例

12. 基于Netty的单体IM系统的开发

完成一个聊天系统的设计和实现

12.1 自定义 ProtoBuf 编解码器

编码时，需要记录POJO的字节码长、魔数、版本号、内容

    public class ProtobufEncoder extends MessageToByteEncoder<ProtoMsg.Message>{
        @Overrider
        protected void encode(ChannelHandlerContext ctx,ProtoMsg.Message msg,ByteBuf out){
            byte[] bytes = msg.toByteArray();
            int length = bytes.length;
            //  写入消息长度,这里用Short，只用两个字节，最大为32767，如果更长可以用writeInt
            out.writeShort(length);
            //  ...省略魔数、版本号等的写入
            //  写入内容
            out.writesBytes(msg.toByteArray());
        }
    }

解码时，按照记录的顺序读取对应长度的内容

    public class ProtobufDecoder extends ByteToMessageDecoder{
        @Override
        protected void decode (ChannelHandlerContext ctx,ByteBuf in,List<Object> out){
            //  标记当前读指针的位置
            in.markReaderIndex();
            if (in.readableBytes() < 2){
                return;
            }
            int length = in.readShort();
            if(length < 0){
                //非法数据，关闭连接
                ctx.close();
            }
            if(length > in.readableBytes()){
                //读到的消息体长度如果小于传送过来的消息长度，重置读取位置重读
                in.resetReaderIndex();
                return;
            }
            //...省略读取魔数、版本号等其他数据
            byte[] array;
            if(in.hasArray()){
                //堆缓冲区
                ByteBuf slice = in.slice();
                array = slice.array();
            }else{
                //直接缓冲区
                array = new byte[length];
                in.readBytes(array,0,length);
            }
            //字节转出Protobuf的POJO对象
            ProtoMsg.message outmsg = ProtoMsg.Message.parseFrom(array);
            if(outmsg != null){
                out.add(outmsg);
            }
        }
    }

12.2 定义消息格式

一般来说，不管是二进制格式、XML、JSON等字符串格式，答题都可以分为3大消息类型：

1. 请求消息
2. 应答消息
3. 命令消息


