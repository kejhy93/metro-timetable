package org.hejnaluk.metrotimetable;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MetroTimetableApplication {

	public static void main(String[] args) {
		SpringApplication.run(MetroTimetableApplication.class, args);
	}

}