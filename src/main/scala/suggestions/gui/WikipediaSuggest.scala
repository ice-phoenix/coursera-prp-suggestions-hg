package suggestions
package gui

import javax.swing.{ListSelectionModel, UIManager}
import scala.collection.mutable.ListBuffer
import scala.collection.JavaConverters._
import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.swing._
import scala.swing.event._
import scala.swing.Orientation._
import scala.swing.Swing._
import scala.util.{Try, Success, Failure}
import rx.lang.scala.Observable
import rx.lang.scala.Subscription
import observablex._
import search._

object WikipediaSuggest extends SimpleSwingApplication with ConcreteSwingApi with ConcreteWikipediaApi {

  {
    try {
      UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
    } catch {
      case t: Throwable =>
    }
  }

  def top = new MainFrame {

    /* gui setup */

    title = "Query Wikipedia"
    minimumSize = new Dimension(900, 600)

    val button = new Button("Get") {
      icon = new javax.swing.ImageIcon(javax.imageio.ImageIO.read(this.getClass.getResourceAsStream("/suggestions/wiki-icon.png")))
    }
    val searchTermField = new TextField
    val suggestionList = new ListView(ListBuffer[String]())
    val status = new Label("...")
    val editorPane = new EditorPane {

      import javax.swing.border._

      border = new EtchedBorder(EtchedBorder.LOWERED)
      editable = false
      peer.setContentType("text/html")
    }

    suggestionList.peer.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)

    contents = new BoxPanel(orientation = Vertical) {
      border = EmptyBorder(top = 5, left = 5, bottom = 5, right = 5)
      contents += new BoxPanel(orientation = Horizontal) {
        contents += new BoxPanel(orientation = Vertical) {
          maximumSize = new Dimension(240, 900)
          border = EmptyBorder(top = 10, left = 10, bottom = 10, right = 10)
          contents += new BoxPanel(orientation = Horizontal) {
            maximumSize = new Dimension(640, 30)
            border = EmptyBorder(top = 5, left = 0, bottom = 5, right = 0)
            contents += searchTermField
          }
          contents += new ScrollPane(suggestionList)
          contents += new BorderPanel {
            maximumSize = new Dimension(640, 30)
            add(button, BorderPanel.Position.Center)
          }
        }
        contents += new ScrollPane(editorPane)
      }
      contents += status
    }

    val eventScheduler = SchedulerEx.SwingEventThreadScheduler

    /**
     * Observables
     * You may find the following methods useful when manipulating GUI elements:
     * `myListView.listData = aList` : sets the content of `myListView` to `aList`
     * `myTextField.text = "react"`  : sets the content of `myTextField` to "react"
     * `myListView.selection.items`  : returns a list of selected items from `myListView`
     * `myEditorPane.text = "act"`   : sets the content of `myEditorPane` to "act"
     */

    val searchTerms: Observable[String] = searchTermField.textValues

    val suggestions: Observable[Try[List[String]]] = searchTerms.flatMap {
      term => wikiSuggestResponseStream(term).recovered
    }

    val suggestionSubscription: Subscription = suggestions.observeOn(eventScheduler).subscribe {
      x => x match {
        case Success(v) => suggestionList.listData = v
        case Failure(e) => println(e.getStackTraceString); status.text = e.getLocalizedMessage
      }
    }

    val selections: Observable[String] = button.clicks.flatMap {
      _ =>
        suggestionList.selection.items.self match {
          case Seq(h, rest@_*) => Observable(h)
          case Seq() => Observable()
        }
    }

    val pages: Observable[Try[String]] = selections.flatMap {
      sel => wikiPageResponseStream(sel).recovered
    }

    val pageSubscription: Subscription = pages.observeOn(eventScheduler).subscribe {
      x => x match {
        case Success(v) => editorPane.text = v
        case Failure(e) => println(e.getStackTraceString); status.text = e.getLocalizedMessage
      }
    }

  }

}


trait ConcreteWikipediaApi extends WikipediaApi {
  def wikipediaSuggestion(term: String) = Search.wikipediaSuggestion(term)
  def wikipediaPage(term: String) = Search.wikipediaPage(term)
}


trait ConcreteSwingApi extends SwingApi {
  type ValueChanged = scala.swing.event.ValueChanged

  object ValueChanged {
    def unapply(x: Event) = x match {
      case vc: ValueChanged => Some(vc.source.asInstanceOf[TextField])
      case _ => None
    }
  }

  type ButtonClicked = scala.swing.event.ButtonClicked

  object ButtonClicked {
    def unapply(x: Event) = x match {
      case bc: ButtonClicked => Some(bc.source.asInstanceOf[Button])
      case _ => None
    }
  }

  type TextField = scala.swing.TextField
  type Button = scala.swing.Button
}
