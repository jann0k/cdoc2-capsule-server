import org.yaml.snakeyaml.Yaml
println 'buildbasedir: ' + properties['buildbasedir']
def yaml = new Yaml()
def openapi = yaml.load(new File(properties['buildbasedir'] + File.separator + 'cdoc2-key-capsules-openapi.yaml').text)
println "cdoc2-key-capsules-openapi.version: ${openapi.info.version}"
project.getProperties().setProperty('cdoc2-key-capsules-openapi.version', openapi.info.version)