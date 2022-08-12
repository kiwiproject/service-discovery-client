FROM netflixoss/tomcat:7.0.64

RUN cd /tomcat/webapps &&\
  mkdir eureka &&\
  cd eureka &&\
  wget -q https://repo1.maven.org/maven2/com/netflix/eureka/eureka-server/1.10.17/eureka-server-1.10.17.war &&\
  jar xf eureka-server-1.10.17.war &&\
  rm eureka-server-1.10.17.war

ADD config.properties /tomcat/webapps/eureka/WEB-INF/classes/config.properties
ADD eureka-client-test.properties /tomcat/webapps/eureka/WEB-INF/classes/eureka-client-test.properties
ADD eureka-server-test.properties /tomcat/webapps/eureka/WEB-INF/classes/eureka-server-test.properties

EXPOSE 8080

ENTRYPOINT ["/tomcat/bin/catalina.sh"]

CMD ["run"]
