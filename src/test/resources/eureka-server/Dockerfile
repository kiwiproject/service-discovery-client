FROM netflixoss/tomcat:7.0.64

RUN mkdir -p /tomcat/webapps/eureka

ADD eureka-server-1.10.17.war /tomcat/webapps/eureka/

RUN cd /tomcat/webapps/eureka && jar xf eureka-server-1.10.17.war && rm eureka-server-1.10.17.war

ADD config.properties /tomcat/webapps/eureka/WEB-INF/classes/config.properties
ADD eureka-client-test.properties /tomcat/webapps/eureka/WEB-INF/classes/eureka-client-test.properties
ADD eureka-server-test.properties /tomcat/webapps/eureka/WEB-INF/classes/eureka-server-test.properties

EXPOSE 8080

ENTRYPOINT ["/tomcat/bin/catalina.sh"]

CMD ["run"]
