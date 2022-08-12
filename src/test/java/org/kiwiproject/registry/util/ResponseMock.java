package org.kiwiproject.registry.util;

import static org.mockito.AdditionalAnswers.answer;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;

import org.mockito.ArgumentMatchers;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ResponseMock {
    
    /**
     * @implNote The entity that is added to the original response must be the same type that is being ultimately read. There will not be any conversions.
     */
    public static Response simulateInbound(Response originalResponse) {
        var responseToReturn = spy(originalResponse);
       
        doAnswer(answer((Class<?> type) -> readEntity(responseToReturn, type)))
          .when(responseToReturn)
          .readEntity(ArgumentMatchers.<Class<?>>any());

        doAnswer(answer((GenericType<?> type) -> readEntity(responseToReturn, type.getRawType())))
          .when(responseToReturn)
          .readEntity(ArgumentMatchers.<GenericType<?>>any());

        return responseToReturn;
      }
       
      // use the getEntity from the real object as
      // the readEntity of the mock
      @SuppressWarnings("unchecked")
      private static <T> T readEntity(Response realResponse, Class<T> t) {
        return (T)realResponse.getEntity();
      }
}
