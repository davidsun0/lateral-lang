

(defmacro prep (a b)
  (let (LinkedList     "io/github/whetfire/lateral/LinkedList"
        LinkedListType "Lio/github/whetfire/lateral/LinkedList;"
        ObjectType     "Ljava/lang/Object;")
    (list (quote asm)
          (list :new LinkedList)
          :dup
          a
          b
          (list :checkcast LinkedList)
          (list :invokespecial LinkedList "<init>"
                "(Ljava/lang/Object;Lio/github/whetfire/lateral/LinkedList;)V"))))

(defun cons1 (a b)
  (prep a b))

(cons1 1 null)
