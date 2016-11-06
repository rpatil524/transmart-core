import inc.oracle.CsvLoader
import inc.oracle.SqlProducer

def sql = SqlProducer.createFromEnv()

System.in.eachLine { String line ->
    def (table, file) = line.split('\t')

    println "Loading ${table}"
    try {
        def csvLoader = new CsvLoader(
                sql: sql,
                table: table,
                file: file,
                delimiter: '\t'
        )
        csvLoader.prepareConnection()
        csvLoader.load()
    } catch (Exception exception) {
        exception.printStackTrace(System.err)
        System.exit 1
    }
}