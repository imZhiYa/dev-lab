# JMH 微基准编排入口（薄壳，转发到 scripts/run-benchmarks.sh）
.PHONY: all tree sync bio jar clean

all: jar
	bash scripts/run-benchmarks.sh all

tree: jar
	bash scripts/run-benchmarks.sh tree

sync: jar
	bash scripts/run-benchmarks.sh sync

bio: jar
	bash scripts/run-benchmarks.sh bio

jar:
	@test -f target/benchmarks.jar || mvn -q clean package -DskipTests

clean:
	mvn -q clean
