package cat.daisy.core.database.tables

import org.jetbrains.exposed.sql.Table

object ContainerTransactionsTable : Table("container_transactions") {
    val id = long("id").autoIncrement()
    val time = integer("time") // epoch seconds
    val playerName = varchar("playerName", 16)
    val action = integer("action")
    val containerType = varchar("container_type", 50)
    val material = varchar("material", 100)
    val amount = integer("amount")
    val world = varchar("world", 50)
    val x = integer("x")
    val y = integer("y")
    val z = integer("z")

    override val primaryKey = PrimaryKey(id)
}
