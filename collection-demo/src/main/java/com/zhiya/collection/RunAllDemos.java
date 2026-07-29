
package com.zhiya.collection;


import com.zhiya.collection.concurrent.ConcurrentMapDemo;
import com.zhiya.collection.concurrent.ConcurrentSkipListMapDemo;
import com.zhiya.collection.concurrent.CopyOnWriteDemo;
import com.zhiya.collection.list.ListDemo;
import com.zhiya.collection.map.EnumMapDemo;
import com.zhiya.collection.map.HashMapDemo;
import com.zhiya.collection.map.IdentityHashMapDemo;
import com.zhiya.collection.map.TreeMapLinkedHashMapDemo;
import com.zhiya.collection.queue.ArrayDequeDemo;
import com.zhiya.collection.queue.BlockingQueueDemo;
import com.zhiya.collection.set.EnumSetDemo;

/**
 * 运行所有 Collection 框架 Demo
 *
 * 按文档 Level 顺序组织：
 * Level 2   → ListDemo（ArrayList vs LinkedList）
 * Level 3-4 → HashMapDemo（哈希、put、树化、扩容）
 * Level 5   → TreeMapLinkedHashMapDemo（TreeMap、LRU）
 * Level 6   → ConcurrentMapDemo（ConcurrentHashMap）
 * Level 7   → CopyOnWriteDemo（Fail-Fast vs Fail-Safe）
 * Level 7.5 → ArrayDequeDemo、BlockingQueueDemo（Queue/Deque）
 * Level 7.6 → EnumSetDemo、EnumMapDemo、IdentityHashMapDemo、ConcurrentSkipListMapDemo
 */
public class RunAllDemos {

    public static void main(String[] args) throws Exception {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║      Java Collection 框架深度解析 · 完整 Demo              ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();

        run("Level 2: ArrayList vs LinkedList", ListDemo::main);
        run("Level 3-4: HashMap 深度解析", HashMapDemo::main);
        run("Level 5 + 5.5: TreeMap & LRU", TreeMapLinkedHashMapDemo::main);
        run("Level 6: ConcurrentHashMap", ConcurrentMapDemo::main);
        run("Level 7: CopyOnWriteArrayList", CopyOnWriteDemo::main);
        run("Level 7.5: ArrayDeque（替代 Stack）", ArrayDequeDemo::main);
        run("Level 7.5: BlockingQueue（生产者-消费者）", BlockingQueueDemo::main);
        run("Level 7.6.3: EnumSet（位运算）", EnumSetDemo::main);
        run("Level 7.6.4: EnumMap（枚举专用）", EnumMapDemo::main);
        run("Level 7.6.5: IdentityHashMap（== 比较）", IdentityHashMapDemo::main);
        run("Level 7.6.6: ConcurrentSkipListMap（跳表）", ConcurrentSkipListMapDemo::main);

        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║                     全部 Demo 运行完毕                      ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
    }

    static void run(String title, ThrowingConsumer<String[]> action) throws Exception {
        System.out.println("┌──────────────────────────────────────────────────────────────┐");
        System.out.printf("│  %s%n", title);
        System.out.println("└──────────────────────────────────────────────────────────────┘");
        action.accept(new String[0]);
        System.out.println("\n" + new String(new char[70]).replace('\0', '=') + "\n");
    }

    @FunctionalInterface
    interface ThrowingConsumer<T> {
        void accept(T t) throws Exception;
    }
}

