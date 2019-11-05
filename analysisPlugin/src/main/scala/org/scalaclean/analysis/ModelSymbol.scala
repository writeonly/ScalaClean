package org.scalaclean.analysis

import scala.collection.immutable.ListSet
import scala.collection.mutable.ListBuffer
import scala.tools.nsc.Global

trait HasModelCommon {
  def common: ModelCommon

  def csvString: String = common.csvString

  def newCsvString: String = common.newCsvString
}

sealed trait ModelSymbol extends HasModelCommon {

  def debugName: String

  val common: ModelCommon
  var traversal:Int = -1

  def isGlobal: Boolean = common.isGlobal

  def semanticRep: String = common.id

  def sourceFile: String = common.sourceFile

  def posStart: Int = common.posStart

  def posEnd: Int = common.posEnd

  //  def  isSynthetic: Boolean
  //  def  isAbstract: Boolean
  //  def  isLazy: Boolean
  def isParameter: Boolean = false

  def sourceName: String = common.sourceName

  val tree: Global#Tree

  def gTree(g: Global): g.Tree = tree.asInstanceOf[g.Tree]

  private var _extensionData: List[ExtensionData] = Nil

  def addExtensionData(additionalData: List[ExtensionData]): Unit = {
    if (_extensionData.isEmpty) _extensionData = additionalData
    else _extensionData = _extensionData.reverse_:::(additionalData)
  }

  def extensionData = _extensionData

  def ioToken: String

  var children = Vector[ModelSymbol]()

  var extendsRels: ListSet[(HasModelCommon, Boolean)] = ListSet.empty
  var overridesRels: ListSet[(HasModelCommon, Boolean)] = ListSet.empty
  var refersRels: ListSet[(HasModelCommon, Boolean)] = ListSet.empty
  var withinRels: ListSet[ModelSymbol] = ListSet.empty
  var gettersFor: ListSet[ModelCommon] = ListSet.empty
  var settersFor: ListSet[ModelCommon] = ListSet.empty

  def addGetterFor(field: ModelCommon): Unit = {
    gettersFor += field
  }

  def addSetterFor(field: ModelCommon): Unit = {
    settersFor += field
  }

  def addExtends(parentSym: HasModelCommon, direct: Boolean): Unit = {
    extendsRels = extendsRels.+((parentSym, direct))
  }

  def addOverride(common: ModelCommon, direct: Boolean): Unit = {
    overridesRels = overridesRels.+((common, direct))
  }

  def addRefers(common: ModelCommon, isSynthetic: Boolean): Unit = {
    refersRels = refersRels.+((common, isSynthetic))
  }

  def addWithin(mSymbol: ModelSymbol): Unit = {
    withinRels = withinRels.+(mSymbol)
  }

  def addChild(modelSymbol: ModelSymbol): Unit = {
    children :+= modelSymbol
  }


  def printStructure(): Unit = {
    printStructureInt(0)
  }

  private def printStructureInt(depth: Int): Unit = {
    if (depth > 0) {
      print("  " * (depth - 1))
      print("+-")
    }
    println(newCsvString + " isLocal = " + !isGlobal + ", isParameter= " + isParameter)

    def printRelStart(): Unit = {
      if (depth > 0) {
        print("  " * (depth - 1))
        print("|   ")
      }
    }

    extendsRels.foreach { case (ext, direct) =>
      printRelStart()
      println(s"extends: $ext  direct=$direct")
    }

    overridesRels.foreach { case (ovr, direct) =>
      printRelStart()
      println(s"overrides: $ovr  direct=$direct")
    }

    withinRels.foreach { within =>
      printRelStart()
      println(s"within: ${within.newCsvString}")
    }


    refersRels.foreach { case (refers, isSynthetic) =>
      printRelStart()
      println(s"refers: ${refers.newCsvString}   isGlobal=${refers.common.isGlobal}")
    }

    gettersFor.foreach { getterTarget =>
      printRelStart()
      println(s"getterFor: ${getterTarget.newCsvString}")
    }

    settersFor.foreach { setterTarget =>
      printRelStart()
      println(s"setterFor: ${setterTarget.newCsvString}")
    }

    children.foreach { child =>
      child.printStructureInt(depth + 1)
    }
  }


  def outputStructure(eleWriter: ElementsWriter, relWriter: RelationshipsWriter, extensionWriter: ExtensionWriter): Unit = {
    eleWriter.write(this)
    extensionWriter.writeExtensions(this)

    extendsRels.foreach { case (ext, direct) =>
      relWriter.extendsCls(ext, this, direct)
    }

    overridesRels.foreach { case (ovr, direct) =>
      // bit of a hack with the cast - could be more specfic on subclasses
      relWriter.overrides(this.asInstanceOf[ModelMethod], ovr, direct)
    }

    withinRels.foreach { within =>
      relWriter.within(within, this)
    }

    refersRels.foreach { case (refers, isSynthetic) =>
      relWriter.refers(this, refers, isSynthetic)
    }

    gettersFor.foreach { getterTarget =>
      relWriter.getterFor(this.common, getterTarget)
    }

    settersFor.foreach { setterTarget =>
      relWriter.setterFor(this.common, setterTarget)
    }

    children.foreach { child =>
      child.outputStructure(eleWriter, relWriter, extensionWriter)
    }
  }

  def flatten(): Unit = {
    children = children.flatMap { child =>
      child.flatten()
      child match {
        case mf: ModelField if (mf.isParameter || !mf.isGlobal) =>
          this.refersRels ++= mf.refersRels
          None
        case _ =>
          Some(child)
      }
    }
    refersRels = refersRels.filter(_._1.common.isGlobal)
    refersRels = refersRels.filter(_._1.common.newId != this.common.newId)
  }

}
sealed trait ClassLike extends ModelSymbol
sealed abstract class ModelField extends ModelSymbol

case class ModelCommon(
  isGlobal: Boolean, id: String, newId: String, sourceFile: String, posStart: Int, posEnd: Int,
  sourceName: String) extends HasModelCommon {
  override def common: ModelCommon = this

  override def csvString: String = {
    val globalStr = if (isGlobal) "G:" else "L:"
    s"$globalStr$id"
  }

  override def newCsvString: String = newId
}

case class ModelVar(
  tree: Global#ValDef, common: ModelCommon, isAbstract: Boolean, override val isParameter: Boolean) extends ModelField {
  override def debugName: String = "var"

  override def ioToken: String = IoTokens.typeVar
}

case class ModelVal(
  tree: Global#ValDef, common: ModelCommon, isAbstract: Boolean, isLazy: Boolean,
  override val isParameter: Boolean) extends ModelField {
  override def debugName: String = "val"

  override def ioToken: String = IoTokens.typeVal
}

sealed trait ModelMethod extends ModelSymbol {
  val tree: Global#DefDef
  val common: ModelCommon
  val isTyped: Boolean
  val isAbstract: Boolean
}

case class ModelGetterMethod(
  tree: Global#DefDef, common: ModelCommon, isTyped: Boolean, isAbstract: Boolean) extends ModelMethod {

  override def debugName: String = "getter"

  override def ioToken: String = IoTokens.typeGetterMethod
}

case class ModelSetterMethod(
  tree: Global#DefDef, common: ModelCommon, isTyped: Boolean, isAbstract: Boolean) extends ModelMethod {
  override def debugName: String = "setter"

  override def ioToken: String = IoTokens.typeSetterMethod
}

case class ModelPlainMethod(
  tree: Global#DefDef, common: ModelCommon, isTyped: Boolean, isAbstract: Boolean) extends ModelMethod {
  override def debugName: String = "def"

  override def ioToken: String = IoTokens.typePlainMethod
}

case class ModelObject(tree: Global#ModuleDef, common: ModelCommon) extends ModelSymbol with ClassLike {
  override def debugName: String = "object"

  override def ioToken: String = IoTokens.typeObject
}

case class ModelClass(tree: Global#ClassDef, common: ModelCommon, isAbstract: Boolean) extends ModelSymbol with ClassLike{
  override def debugName: String = "class"

  override def ioToken: String = IoTokens.typeClass
}

case class ModelTrait(tree: Global#ClassDef, common: ModelCommon) extends ModelSymbol with ClassLike{
  override def debugName: String = "trait"

  override def ioToken: String = IoTokens.typeTrait
}

case class ModelSource(tree: Global#Tree, common: ModelCommon) extends ModelSymbol {


  override def debugName: String = "source"

  override def ioToken: String = IoTokens.typeSource
}
