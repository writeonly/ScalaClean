package scalaclean.util

import scalafix.patch.Patch
import scalafix.v1._

import scala.meta.{Defn, Pkg, Term}

class DefaultTreeVisitor()(implicit doc: SemanticDocument) extends TreeVisitor {

  def handleVar(symbol: Symbol, varDef: Defn.Var,scope: List[Scope]): (Patch, Boolean) = {
    println(s"handleVar $symbol - scope $scope")
    (Patch.empty, true)
  }

  def handleVal(symbol: Symbol, valDef: Defn.Val,scope: List[Scope]): (Patch, Boolean) = {
    println(s"handleVal $symbol - scope $scope")
    (Patch.empty, true)
  }

  override def handlePackage(packageName: Term.Name, pkg: Pkg,scope: List[Scope]): (Patch, Boolean) = {
    println(s"handlePackage $packageName - scope $scope")
    (Patch.empty, true)
  }

  override def handleMethod(methodName: Symbol, fullSig: String, method: Defn.Def,scope: List[Scope]): (Patch, Boolean) = {
    println(s"handleMethod $fullSig - scope $scope")
    (Patch.empty, true)
  }

  override def handleObject(objName: Symbol, obj: Defn.Object,scope: List[Scope]): (Patch, Boolean) = {
    println(s"handleObject $objName - scope $scope")
    (Patch.empty, true)
  }

  override def handleClass(clsSymbol: Symbol, cls: Defn.Class,scope: List[Scope]): (Patch, Boolean) = {
    println(s"handleClass $clsSymbol - scope $scope")
    (Patch.empty, true)
  }
}
