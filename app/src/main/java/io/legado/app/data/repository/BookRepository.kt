package io.legado.app.data.repository

import io.legado.app.data.appDb
import io.legado.app.data.dao.BookChapterDao
import io.legado.app.data.dao.BookDao
import io.legado.app.data.entities.Book
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BookRepository(
    private val bookDao: BookDao, //依赖DAO
    private val bookChapterDao: BookChapterDao
) {
    //根据书名和作者获取书籍封面
    suspend fun getBookCoverByNameAndAuthor(bookName: String, bookAuthor: String): String? {
        return withContext(Dispatchers.IO) {
            bookDao.getBook(bookName, bookAuthor)?.getDisplayCover()
        }
    }

    //根据书名和作者获取章节标题
    suspend fun getChapterTitle(bookName: String, bookAuthor: String, chapterIndex: Int): String? {
        return withContext(Dispatchers.IO) {
            val book = bookDao.getBook(bookName, bookAuthor)
            val bookUrl = book?.bookUrl
            if (bookUrl.isNullOrEmpty()) return@withContext null

            bookChapterDao.getChapterTitleByUrlAndIndex(bookUrl, chapterIndex)
        }
    }

    //根据书籍URL获取书籍信息
    suspend fun getBook(bookUrl: String): Book? {
        return appDb.bookDao.getBook(bookUrl)
    }

}
