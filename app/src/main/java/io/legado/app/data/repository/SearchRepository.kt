package io.legado.app.data.repository

import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.SearchKeyword
import io.legado.app.help.book.isNotShelf
import io.legado.app.model.webBook.SearchModel
import io.legado.app.ui.book.search.BookKey
import io.legado.app.ui.book.search.SearchScope
import io.legado.app.ui.main.bookshelf.BookShelfItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * 搜索仓库接口
 *
 * 职责：封装搜索相关的所有数据访问操作
 *
 * 设计模式：接口与实现分离
 * - SearchRepository: 定义搜索业务的 API 规范
 * - SearchRepositoryImpl: 具体实现
 *
 * @see SearchRepositoryImpl
 */
interface SearchRepository {

    /**
     * 可用的书源分组列表
     *
     * Flow 特性：数据变化时自动通知订阅者
     *
     * @return Flow<List<String>> 分组名称列表，如 ["中文", "英文", "VIP"]
     */
    val enabledGroups: Flow<List<String>>

    /**
     * 可用的书源列表（部分字段）
     *
     * 使用 BookSourcePart 而非完整 BookSource，减少内存占用
     *
     * @return Flow<List<BookSourcePart>> 书源列表
     */
    val enabledSources: Flow<List<BookSourcePart>>

    /**
     * 书架中的书籍关键字集合
     *
     * 用于搜索时匹配本地书籍
     *
     * @return Flow<Set<BookKey>> 书籍关键字集合
     */
    val bookshelfKeys: Flow<Set<BookKey>>

    /**
     * 搜索书架中的书籍
     *
     * @param query 搜索关键词
     * @return Flow<List<BookShelfItem>> 匹配的书籍列表
     */
    fun searchBookshelf(query: String): Flow<List<BookShelfItem>>

    /**
     * 搜索历史记录
     *
     * @param query 搜索关键词，为空则返回所有历史
     * @return Flow<List<SearchKeyword>> 匹配的历史记录
     */
    fun searchHistory(query: String): Flow<List<SearchKeyword>>

    /**
     * 保存搜索关键词到历史记录
     *
     * 如果关键词已存在，则增加使用次数
     *
     * @param keyword 搜索关键词
     */
    suspend fun saveSearchKeyword(keyword: String)

    /**
     * 删除单条搜索历史
     *
     * @param item 要删除的搜索历史记录
     */
    suspend fun deleteSearchKeyword(item: SearchKeyword)

    /**
     * 清空所有搜索历史
     */
    suspend fun clearSearchKeywords()

    /**
     * 创建一个新的搜索会话
     *
     * 搜索会话用于管理一次完整的搜索流程，包括：
     * - 启动搜索
     * - 暂停/恢复搜索
     * - 停止搜索
     * - 监听搜索进度
     *
     * @param scopeProvider 获取当前搜索范围的回调
     *                      由于 ViewModel 可能随时更新搜索范围，
     *                      所以用 lambda 延迟获取而非直接传值
     * @return SearchSession 搜索会话实例
     */
    fun createSearchSession(scopeProvider: () -> SearchScope): SearchSession
}

/**
 * 搜索会话事件
 *
 * 使用 sealed interface 定义封闭的事件类型集合
 * 好处：编译器会检查所有可能的事件类型，不会遗漏
 *
 * 使用场景：
 * - Flow<SearchSessionEvent> 用于观察搜索进度
 * - when 表达式可以 exhaustive（覆盖所有情况）
 */
sealed interface SearchSessionEvent {

    /**
     * 搜索开始事件
     */
    data object Started : SearchSessionEvent

    /**
     * 搜索进度事件
     *
     * @property books 当前批次搜索到的书籍
     * @property processedSources 已处理的书源数量
     * @property totalSources 总书源数量
     */
    data class Progress(
        val books: List<SearchBook>,
        val processedSources: Int,
        val totalSources: Int,
    ) : SearchSessionEvent

    /**
     * 搜索完成事件
     *
     * @property isEmpty 是否没有搜索结果
     * @property hasMore 是否还有更多结果（分页用）
     */
    data class Finished(
        val isEmpty: Boolean,
        val hasMore: Boolean,
    ) : SearchSessionEvent

    /**
     * 搜索取消事件
     *
     * @property throwable 取消原因（可选）
     */
    data class Canceled(val throwable: Throwable? = null) : SearchSessionEvent
}

/**
 * 搜索会话接口
 *
 * 用于管理一次搜索的生命周期
 *
 * 使用方式：
 * ```
 * val session = repository.createSearchSession { searchScope }
 * session.events.collect { event ->
 *     when (event) {
 *         is SearchSessionEvent.Started -> { ... }
 *         is SearchSessionEvent.Progress -> { ... }
 *         is SearchSessionEvent.Finished -> { ... }
 *         is SearchSessionEvent.Canceled -> { ... }
 *     }
 * }
 * session.search(searchId, "关键词")
 * ```
 */
interface SearchSession {

    /**
     * 搜索事件流
     *
     * 订阅此 Flow 可以接收搜索过程中的所有事件
     */
    val events: Flow<SearchSessionEvent>

    /**
     * 开始搜索
     *
     * @param searchId 搜索 ID，用于区分多次搜索
     * @param keyword 搜索关键词
     */
    fun search(searchId: Long, keyword: String)

    /**
     * 停止搜索
     *
     * 立即终止搜索，取消所有正在进行的网络请求
     */
    fun stop()

    /**
     * 暂停搜索
     *
     * 暂停当前搜索，但保留搜索状态，可以恢复
     */
    fun pause()

    /**
     * 恢复搜索
     *
     * 继续被暂停的搜索
     */
    fun resume()

    /**
     * 关闭会话
     *
     * 释放所有资源，包括协程作用域
     * 调用后此会话实例不能再使用
     */
    fun close()
}

/**
 * 搜索仓库实现类
 *
 * 实现 SearchRepository 接口，封装所有搜索相关的数据操作
 *
 * 依赖注入：
 * - 通过构造函数接收 AppDatabase 实例
 * - 使用 Koin 的 get() 自动解析依赖
 *
 * @property appDb 应用数据库实例
 */
class SearchRepositoryImpl(
    private val appDb: AppDatabase,
) : SearchRepository {

    /**
     * 获取可用的书源分组
     *
     * 直接委托给 BookSourceDao 的 Flow 方法
     * 当数据库变化时，Flow 自动发射新数据
     */
    override val enabledGroups: Flow<List<String>> = appDb.bookSourceDao.flowEnabledGroups()

    /**
     * 获取可用的书源列表（部分字段）
     */
    override val enabledSources: Flow<List<BookSourcePart>> = appDb.bookSourceDao.flowEnabled()

    /**
     * 获取书架中的书籍关键字
     *
     * 流程：
     * 1. 从 bookDao 获取书架中的所有书籍
     * 2. 过滤掉不在书架中的书籍（isNotShelf）
     * 3. 转换为 BookKey 集合
     *
     * map 操作符：转换 Flow 中的数据
     */
    override val bookshelfKeys: Flow<Set<BookKey>> = appDb.bookDao.flowBookShelf().map { books ->
        books.filterNot { it.isNotShelf }  // 过滤：保留在书架中的书
            .map { book -> BookKey(book.name, book.author, book.bookUrl) }
            .toSet()  // 转换为 Set 去重
    }

    /**
     * 搜索书架中的书籍
     *
     * @param query 搜索关键词
     * @return Flow<List<BookShelfItem>> 匹配的书籍列表
     */
    override fun searchBookshelf(query: String): Flow<List<BookShelfItem>> {
        val keyword = query.trim()  // 去除首尾空白
        return if (keyword.isBlank()) {
            // 关键词为空，返回空列表
            flowOf(emptyList())
        } else {
            // 委托给 DAO 处理
            appDb.bookDao.flowBookShelfSearch(keyword)
        }
    }

    /**
     * 搜索历史记录
     *
     * @param query 搜索关键词，为空则返回所有历史（按时间排序）
     * @return Flow<List<SearchKeyword>> 匹配的历史记录
     */
    override fun searchHistory(query: String): Flow<List<SearchKeyword>> {
        val keyword = query.trim()
        return if (keyword.isBlank()) {
            // 关键词为空，返回所有历史（按时间排序）
            appDb.searchKeywordDao.flowByTime()
        } else {
            // 关键词不为空，搜索匹配的历史
            appDb.searchKeywordDao.flowSearch(keyword)
        }
    }

    /**
     * 保存搜索关键词到历史记录
     *
     * 逻辑：
     * 1. 如果关键词已存在，更新使用次数和使用时间
     * 2. 如果关键词不存在，创建新记录
     *
     * @param keyword 搜索关键词
     */
    override suspend fun saveSearchKeyword(keyword: String) = withContext(Dispatchers.IO) {
        val key = keyword.trim()
        if (key.isBlank()) return@withContext  // 空关键词不保存

        // 查询是否已存在
        appDb.searchKeywordDao.get(key)?.let { history ->
            // 存在：更新使用次数和时间
            history.usage += 1
            history.lastUseTime = System.currentTimeMillis()
            appDb.searchKeywordDao.update(history)
        } ?: run {
            // 不存在：创建新记录
            appDb.searchKeywordDao.insert(SearchKeyword(word = key, usage = 1))
        }
    }

    /**
     * 删除单条搜索历史
     */
    override suspend fun deleteSearchKeyword(item: SearchKeyword) = withContext(Dispatchers.IO) {
        appDb.searchKeywordDao.delete(item)
    }

    /**
     * 清空所有搜索历史
     */
    override suspend fun clearSearchKeywords() = withContext(Dispatchers.IO) {
        appDb.searchKeywordDao.deleteAll()
    }

    /**
     * 创建搜索会话
     *
     * @param scopeProvider 获取当前搜索范围的回调
     * @return SearchSession 搜索会话实例
     */
    override fun createSearchSession(scopeProvider: () -> SearchScope): SearchSession {
        return SearchSessionImpl(scopeProvider)
    }

    /**
     * 搜索会话实现类
     *
     * 私有内部类，封装搜索会话的完整逻辑
     *
     * @property scopeProvider 获取搜索范围的回调
     */
    private class SearchSessionImpl(
        scopeProvider: () -> SearchScope,
    ) : SearchSession {

        /**
         * 会话专属的协程作用域
         *
         * SupervisorJob：子协程失败不影响父协程
         * Dispatchers.IO：适合 IO 密集型任务（网络请求）
         *
         * 生命周期：
         * - 创建：初始化时
         * - 销毁：close() 调用时
         */
        private val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /**
         * 事件流
         *
         * MutableSharedFlow：可变的共享流，可以发射新事件
         * extraBufferCapacity = 64：额外缓冲容量，防止背压
         *
         * asSharedFlow()：转换为只读流，外部只能观察不能修改
         */
        private val _events = MutableSharedFlow<SearchSessionEvent>(extraBufferCapacity = 64)
        override val events: Flow<SearchSessionEvent> = _events.asSharedFlow()

        /**
         * 搜索模型
         *
         * 真正执行搜索的核心类
         * 监听搜索进度，通过 CallBack 回调通知
         */
        private val searchModel = SearchModel(sessionScope, object : SearchModel.CallBack {

            /**
             * 获取当前搜索范围
             *
             * 每次回调时重新获取，保证使用最新的搜索范围
             */
            override fun getSearchScope(): SearchScope = scopeProvider()

            /**
             * 搜索开始回调
             */
            override fun onSearchStart() {
                _events.tryEmit(SearchSessionEvent.Started)
            }

            /**
             * 搜索成功回调
             *
             * @param searchBooks 当前批次搜索到的书籍
             * @param processedSources 已处理的书源数量
             * @param totalSources 总书源数量
             */
            override fun onSearchSuccess(
                searchBooks: List<SearchBook>,
                processedSources: Int,
                totalSources: Int,
            ) {
                _events.tryEmit(
                    SearchSessionEvent.Progress(
                        books = searchBooks.toList(),  // toList() 创建快照，防止外部修改
                        processedSources = processedSources,
                        totalSources = totalSources,
                    )
                )
            }

            /**
             * 搜索完成回调
             */
            override fun onSearchFinish(isEmpty: Boolean, hasMore: Boolean) {
                _events.tryEmit(SearchSessionEvent.Finished(isEmpty, hasMore))
            }

            /**
             * 搜索取消回调
             */
            override fun onSearchCancel(exception: Throwable?) {
                _events.tryEmit(SearchSessionEvent.Canceled(exception))
            }
        })

        /**
         * 开始搜索
         */
        override fun search(searchId: Long, keyword: String) {
            searchModel.search(searchId, keyword)
        }

        /**
         * 停止搜索
         */
        override fun stop() {
            searchModel.cancelSearch()
        }

        /**
         * 暂停搜索
         */
        override fun pause() {
            searchModel.pause()
        }

        /**
         * 恢复搜索
         */
        override fun resume() {
            searchModel.resume()
        }

        /**
         * 关闭会话
         *
         * 重要：必须调用此方法释放资源
         */
        override fun close() {
            searchModel.close()  // 关闭搜索模型
            sessionScope.cancel()  // 取消协程作用域
        }
    }
}
