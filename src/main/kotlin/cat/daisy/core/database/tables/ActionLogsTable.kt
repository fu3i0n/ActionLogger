package cat.daisy.core.database.tables

import org.jetbrains.exposed.sql.Table

object ActionLogsTable : Table("logs") {
    val id = long("id").autoIncrement()
    val time = integer("time") // epoch seconds
    val playerName = varchar("playerName", length = 16)
    val action = integer("action") // was tinyint in MySQL
    val detail = varchar("detail", length = 255).nullable()
    val world = varchar("world", length = 50)
    val x = integer("x")
    val y = integer("y")
    val z = integer("z")
    val amount = integer("amount").default(1)

    override val primaryKey = PrimaryKey(id)

    init {
        index(false, time)
        index(false, playerName, time)
    }
}
