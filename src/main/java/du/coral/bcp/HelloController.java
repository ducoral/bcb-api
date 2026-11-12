package du.coral.bcp;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

  @GetMapping("/")
  public String index() {
    return """
           <html><body>
           <h1>Bem-vindo à API do Big Chat Brasil!</h1>
           </body></html>
           """;
  }

}