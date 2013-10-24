@Grab(group='net.sf.opencsv', module='opencsv', version='2.3')
import au.com.bytecode.opencsv.CSVReader
import groovy.sql.Sql

def parseOptions() {
  def cli = new CliBuilder(usage: "load_tsv_file.groovy -t table -f file")
  cli.f('which file', required: true, longOpt: "file", args: 1)
  cli.t('which table', required: true, longOpt: "table", args: 1)
  def options = cli.parse(args)
  options
}

def setupDatabaseConnection() {
  def driver = "oracle.jdbc.driver.OracleDriver"
  def jdbcUrl = "jdbc:oracle:thin:@${System.getenv('ORAHOST')}:${System.getenv('ORAPORT')}:${System.getenv('ORASID')}"
  def username = 'tm_cz'
  def password = 'tm_cz'
  Sql sql = Sql.newInstance jdbcUrl, username, password, driver
  sql
}

def uploadTsvFileToTable(file, table) {
  sql = setupDatabaseConnection()
  CSVReader reader = new CSVReader(new FileReader(file), '\t'.toCharacter());
  int i = 0
  String [] nextLine;
  //keep memory load by doing one by one
  while ((nextLine = reader.readNext()) != null) {
    print '.'
    i++
    if (i % 500 == 0) { print i }

    sql.execute(
    "INSERT INTO ${table}(gpl_id, probe_id, gene_symbol, gene_id, organism)" +
    " VALUES ('${nextLine[0]}', '${nextLine[1]}', '${nextLine[2]}', '${nextLine[3]}', '${nextLine[4]}')" as String
    )
  }
  print '\n'
  sql.close()
}

def truncateTable(table) {
  sql = setupDatabaseConnection()
  sql.execute("TRUNCATE TABLE $table" as String)
  sql.close()
}

//def loadAnnotationParams() {
  //def annotationParams = new File('')
//}

//def uploadPlatformGplInfo() {
  //loadAnnotationParams()
  //sql = setupDatabaseConnection()
  //sql.
//}

options = parseOptions()
if (!options) { return }

truncateTable(options.table)
uploadTsvFileToTable(options.file, options.table)
