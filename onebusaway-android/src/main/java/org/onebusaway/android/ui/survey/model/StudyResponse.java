package org.onebusaway.android.ui.survey.model;

import java.io.Serializable;
import java.lang.Integer;
import java.lang.String;
import java.util.List;

public class StudyResponse implements Serializable {
  private List<Surveys> surveys;

  private Region region;

  public List<Surveys> getSurveys() {
    return this.surveys;
  }

  public void setSurveys(List<Surveys> surveys) {
    this.surveys = surveys;
  }

  public Region getRegion() {
    return this.region;
  }

  public void setRegion(Region region) {
    this.region = region;
  }

  public static class Surveys implements Serializable {
    private Study study;

    private String name;

    private List<Questions> questions;

    private String created_at;

    private Integer id;

    public Study getStudy() {
      return this.study;
    }

    public void setStudy(Study study) {
      this.study = study;
    }

    public String getName() {
      return this.name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public List<Questions> getQuestions() {
      return this.questions;
    }



    public String getCreated_at() {
      return this.created_at;
    }


    public Integer getId() {
      return this.id;
    }

    public void setId(Integer id) {
      this.id = id;
    }

    public static class Study implements Serializable {
      private String name;

      private String description;

      private Integer id;

      public String getName() {
        return this.name;
      }

      public void setName(String name) {
        this.name = name;
      }

      public String getDescription() {
        return this.description;
      }

      public void setDescription(String description) {
        this.description = description;
      }

      public Integer getId() {
        return this.id;
      }

      public void setId(Integer id) {
        this.id = id;
      }
    }

    public static class Questions implements Serializable {
      private Integer id;

      private Integer position;

      private Content content;

      public Integer getId() {
        return this.id;
      }

      public void setId(Integer id) {
        this.id = id;
      }

      public Integer getPosition() {
        return this.position;
      }

      public void setPosition(Integer position) {
        this.position = position;
      }

      public Content getContent() {
        return this.content;
      }

      public void setContent(Content content) {
        this.content = content;
      }

      public static class Content implements Serializable {
        private String label_text;

        private List<String> options;

        private String type;

        public String getLabel_text() {
          return this.label_text;
        }

        public List<String> getOptions() {
          return this.options;
        }

        public void setOptions(List<String> options) {
          this.options = options;
        }

        public String getType() {
          return this.type;
        }

        public void setType(String type) {
          this.type = type;
        }
      }
    }
  }

  public static class Region implements Serializable {
    private String name;

    private Integer id;

    public String getName() {
      return this.name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public Integer getId() {
      return this.id;
    }

    public void setId(Integer id) {
      this.id = id;
    }
  }
}
